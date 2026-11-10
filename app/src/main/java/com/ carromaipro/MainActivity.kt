package com.carromaipro

import android.app.*
import androidx.activity.ComponentActivity
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private lateinit var overlay: AimOverlayView
    private lateinit var status: TextView
    private lateinit var resultText: TextView
    private var currentBitmap: Bitmap? = null
    private var bounds: BoardRect? = null
    private val discs = mutableListOf<Disc>()
    private var striker: Disc? = null
    private var selectedShot: Shot? = null
    private var tapMode = "striker"
    private var shots: List<Shot> = emptyList()

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }
    private val projectionPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val i=Intent(this,CaptureService::class.java).putExtra("code",result.resultCode).putExtra("data",result.data)
            startForegroundService(i)
            status.text="Screen capture started. Switch to the carrom game; capture service saves a frame when available."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        CaptureService.onFrame = { bmp -> runOnUiThread { useBitmap(bmp) } }
    }

    private fun buildUi() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,12,16,12); setBackgroundColor(0xFF101820.toInt()) }
        val title=TextView(this).apply { text="CARROM AI PRO"; textSize=25f; setTextColor(0xFFFFFFFF.toInt()); gravity=Gravity.CENTER; setPadding(0,8,0,8) }
        root.addView(title)
        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        buttons.addView(btn("Gallery screenshot"){imagePicker.launch("image/*")},weight())
        buttons.addView(btn("Live capture"){ val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager; projectionPicker.launch(mgr.createScreenCaptureIntent()) },weight())
        root.addView(buttons)
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(btn("Auto detect"){autoDetect()},weight())
        actions.addView(btn("Clear"){discs.clear();striker=null;selectedShot=null;refresh()},weight())
        root.addView(actions)
        status=TextView(this).apply { text="Import a carrom screenshot or start live screen capture."; setTextColor(0xFFE5E7EB.toInt()); textSize=13f; setPadding(4,8,4,8) }
        root.addView(status)
        overlay=AimOverlayView(this).apply { setBackgroundColor(0xFF263238.toInt()); onBoardTap={p->handleTap(p)} }
        root.addView(overlay,LinearLayout.LayoutParams(-1,0,1f))
        val modes=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        modes.addView(btn("Set striker"){tapMode="striker";status.text="Tap the striker center on the screenshot."},weight())
        modes.addView(btn("Add coin"){tapMode="coin";status.text="Tap a coin center. Repeat for more coins."},weight())
        modes.addView(btn("Remove nearest"){tapMode="remove";status.text="Tap a disc to remove it."},weight())
        root.addView(modes)
        root.addView(btn("Calculate best shots"){calculateShots()})
        resultText=TextView(this).apply { text="No calculation yet."; setTextColor(0xFFFFFFFF.toInt()); textSize=14f; setPadding(4,8,4,8) }
        root.addView(resultText)
        root.addView(btn("Next best shot"){if(shots.isNotEmpty()){val idx=shots.indexOf(selectedShot).let{if(it<0)0 else (it+1)%shots.size};selectedShot=shots[idx];showShot(idx)} })
        setContentView(root)
    }
    private fun weight()=LinearLayout.LayoutParams(0,-2,1f)
    private fun btn(label:String, action:()->Unit)=Button(this).apply {
        text=label; isAllCaps=false; setOnClickListener { action() }
        layoutParams=LinearLayout.LayoutParams(-1,-2,1f)
    }
    private fun loadImage(uri:Uri) {
        try { contentResolver.openInputStream(uri).use { stream -> val bmp=BitmapFactory.decodeStream(stream) ?: return; useBitmap(bmp) } }
        catch(e:Exception){status.text="Could not open image: ${e.message}"}
    }
    private fun useBitmap(bmp:Bitmap) {
        currentBitmap=bmp.copy(Bitmap.Config.ARGB_8888,true)
        overlay.bitmap=currentBitmap
        status.text="Screenshot loaded: ${bmp.width} × ${bmp.height}. Run auto detect, then correct the striker/coins manually."
        autoDetect()
    }
    private fun autoDetect() {
        val bmp=currentBitmap ?: run { status.text="Import a screenshot first."; return }
        val detection=BoardDetector.detect(bmp)
        bounds=detection.bounds
        discs.clear(); discs.addAll(detection.discs)
        striker=null; selectedShot=null; shots=emptyList()
        status.text="${detection.message} Detection confidence estimate: ${(detection.confidence*100).roundToInt()}%. Tap Set striker and mark it manually."
        refresh()
    }
    private fun handleTap(p:Vec2) {
        if(bounds?.contains(p)!=true){status.text="Tap inside the estimated board area.";return}
        when(tapMode){
            "striker" -> {
                val radius=estimatedRadius()
                striker=Disc("STRIKER",p,radius,Disc.Kind.STRIKER)
                discs.removeAll{it.kind==Disc.Kind.STRIKER}
                discs.add(0,striker!!)
                status.text="Striker set. Add or correct coins, then calculate."
            }
            "coin" -> {
                val radius=estimatedRadius()
                val d=Disc("C${discs.count{it.kind!=Disc.Kind.STRIKER}+1}",p,radius,Disc.Kind.UNKNOWN)
                discs.add(d); status.text="Added ${d.id}. Tap more coins or calculate."
            }
            "remove" -> {
                val nearest=discs.minByOrNull{it.center.distance(p)}
                if(nearest!=null && nearest.center.distance(p)<nearest.radius*3){discs.remove(nearest);if(nearest.kind==Disc.Kind.STRIKER)striker=null}
                status.text="Nearest disc removed if it was close to your tap."
            }
        }
        refresh()
    }
    private fun estimatedRadius()=max(7.0,(bounds?.width ?: 500.0)*0.018)
    private fun calculateShots() {
        val b=bounds ?: run{status.text="Detect board first.";return}
        val s=striker ?: discs.firstOrNull{it.kind==Disc.Kind.STRIKER}
        if(s==null){status.text="Set striker first.";return}
        val targets=discs.filter{it.id!=s.id}
        if(targets.isEmpty()){status.text="Add at least one target coin.";return}
        shots=AimPlanner.plan(s,targets,b)
        if(shots.isEmpty()){resultText.text="No clear path found. Check board bounds, coin centers and radii.";selectedShot=null}
        else {selectedShot=shots.first();showShot(0)}
        refresh()
    }
    private fun showShot(index:Int) {
        val s=selectedShot ?: return
        resultText.text="Best candidate ${index+1}/${shots.size}\nTarget: ${s.targetId}\nPocket: (${s.pocket.x.roundToInt()}, ${s.pocket.y.roundToInt()})\nCut angle: ${"%.1f".format(s.cutAngleDeg)}°\nPath length: ${"%.0f".format(s.distance)} px\nBank cushions: ${s.bankCount}\nGeometry score: ${"%.1f".format(s.score)}/100\n${s.note}\nYellow = striker path; magenta = coin path. Score is heuristic, not a success probability."
        refresh()
    }
    private fun refresh() { overlay.board=bounds;overlay.discs=discs.toList();overlay.selectedShot=selectedShot;overlay.invalidate() }
}
