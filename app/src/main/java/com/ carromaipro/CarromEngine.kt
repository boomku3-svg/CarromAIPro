package com.carromaipro

import kotlin.math.*

/** All geometry uses image pixels. Physics values are normalized because screenshot scale is unknown. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    operator fun div(k: Double) = Vec2(x / k, y / k)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun length() = hypot(x, y)
    fun normalized(): Vec2 = if (length() < 1e-9) Vec2(0.0, 0.0) else this / length()
    fun distance(o: Vec2) = (this - o).length()
    fun reflectX(minX: Double, maxX: Double) = Vec2(2.0 * minX - x, y).let {
        if (x < minX) it else Vec2(2.0 * maxX - x, y)
    }
    fun reflectY(minY: Double, maxY: Double) = Vec2(x, if (y < minY) 2 * minY - y else 2 * maxY - y)
}

data class BoardRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(p: Vec2, margin: Double = 0.0) =
        p.x >= left + margin && p.x <= right - margin &&
        p.y >= top + margin && p.y <= bottom - margin
    fun pockets(inset: Double = 0.0) = listOf(
        Vec2(left + inset, top + inset), Vec2((left + right) / 2, top + inset),
        Vec2(right - inset, top + inset), Vec2(left + inset, bottom - inset),
        Vec2((left + right) / 2, bottom - inset), Vec2(right - inset, bottom - inset)
    )
}

data class Disc(val id: String, val center: Vec2, val radius: Double, val kind: Kind) {
    enum class Kind { STRIKER, OWN, OPPONENT, QUEEN, UNKNOWN }
}
data class Shot(val targetId: String, val pocket: Vec2, val strikerPath: List<Vec2>,
                val coinPath: List<Vec2>, val cutAngleDeg: Double, val distance: Double,
                val bankCount: Int, val score: Double, val note: String)

data class PhysicsConfig(
    val restitution: Double = 0.88,
    val friction: Double = 0.018,
    val rollingResistance: Double = 0.012,
    val strikerMass: Double = 1.0,
    val coinMass: Double = 0.82,
    val maxSimulationSeconds: Double = 4.0,
    val timeStep: Double = 1.0 / 120.0
)

object CarromMath {
    fun distancePointToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val t = if (ab.dot(ab) == 0.0) 0.0 else ((p - a).dot(ab) / ab.dot(ab)).coerceIn(0.0, 1.0)
        return p.distance(a + ab * t)
    }

    fun angleBetween(a: Vec2, b: Vec2): Double {
        val denom = a.length() * b.length()
        if (denom < 1e-9) return 0.0
        return Math.toDegrees(acos((a.dot(b) / denom).coerceIn(-1.0, 1.0)))
    }

    /** Contact point for a striker to send target coin toward a pocket. */
    fun ghostBallCenter(target: Vec2, pocket: Vec2, combinedRadius: Double): Vec2 {
        val towardPocket = (target - pocket).normalized()
        return target + towardPocket * combinedRadius
    }

    fun lineCircleIntersection(origin: Vec2, direction: Vec2, center: Vec2, radius: Double): List<Vec2> {
        val d = direction.normalized()
        val f = origin - center
        val a = d.dot(d)
        val b = 2.0 * f.dot(d)
        val c = f.dot(f) - radius * radius
        val disc = b * b - 4 * a * c
        if (disc < 0) return emptyList()
        val s = sqrt(disc)
        return listOf((-b - s) / (2 * a), (-b + s) / (2 * a))
            .filter { it >= 0 }.map { origin + d * it }
    }

    fun segmentIntersectsDisc(a: Vec2, b: Vec2, disc: Disc, clearance: Double = 0.0) =
        distancePointToSegment(disc.center, a, b) < disc.radius + clearance
}

object CarromPhysics {
    /** Equal-plane disc collision impulse, with configurable masses and restitution. */
    fun collide(vA: Vec2, vB: Vec2, normalAtoB: Vec2, massA: Double, massB: Double,
                restitution: Double): Pair<Vec2, Vec2> {
        val n = normalAtoB.normalized()
        val relative = (vA - vB).dot(n)
        if (relative <= 0.0) return vA to vB
        val impulse = (1.0 + restitution) * relative / (1.0 / massA + 1.0 / massB)
        return (vA - n * (impulse / massA)) to (vB + n * (impulse / massB))
    }

    /** Semi-implicit Euler integration with rolling resistance and wall restitution. */
    fun simulate(start: Vec2, initialVelocity: Vec2, bounds: BoardRect, radius: Double,
                 config: PhysicsConfig = PhysicsConfig()): List<Vec2> {
        var p = start
        var v = initialVelocity
        val points = mutableListOf(p)
        var t = 0.0
        while (t < config.maxSimulationSeconds && v.length() > 0.8) {
            val speed = v.length()
            val decel = config.rollingResistance + config.friction * speed
            v = if (speed <= decel * config.timeStep) Vec2(0.0, 0.0)
                else v - v.normalized() * decel * config.timeStep
            p += v * config.timeStep
            if (p.x < bounds.left + radius) { p = Vec2(bounds.left + radius, p.y); v = Vec2(-v.x * config.restitution, v.y) }
            if (p.x > bounds.right - radius) { p = Vec2(bounds.right - radius, p.y); v = Vec2(-v.x * config.restitution, v.y) }
            if (p.y < bounds.top + radius) { p = Vec2(p.x, bounds.top + radius); v = Vec2(v.x, -v.y * config.restitution) }
            if (p.y > bounds.bottom - radius) { p = Vec2(p.x, bounds.bottom - radius); v = Vec2(v.x, -v.y * config.restitution) }
            if (points.size % 4 == 0) points.add(p)
            t += config.timeStep
        }
        points.add(p)
        return points
    }
}

object AimPlanner {
    /** Evaluate direct and one-cushion virtual-pocket solutions. This is a geometric planner,
     * not a guarantee: spin, board condition and exact game physics require calibration. */
    fun plan(striker: Disc, targets: List<Disc>, bounds: BoardRect,
             pockets: List<Vec2> = bounds.pockets(), physics: PhysicsConfig = PhysicsConfig()): List<Shot> {
        val shots = mutableListOf<Shot>()
        val blockers = targets.filter { it.id != striker.id }
        for (target in targets.filter { it.kind != Disc.Kind.STRIKER }) {
            for (pocket in pockets) {
                val ghost = CarromMath.ghostBallCenter(target.center, pocket, striker.radius + target.radius)
                addCandidate(shots, striker, target, pocket, ghost, blockers, bounds, 0, physics)
                // Mirror-pocket construction for four cushion edges.
                val mirrors = listOf(
                    Vec2(2 * bounds.left - pocket.x, pocket.y),
                    Vec2(2 * bounds.right - pocket.x, pocket.y),
                    Vec2(pocket.x, 2 * bounds.top - pocket.y),
                    Vec2(pocket.x, 2 * bounds.bottom - pocket.y)
                )
                for (m in mirrors) {
                    val virtualGhost = CarromMath.ghostBallCenter(target.center, m, striker.radius + target.radius)
                    addCandidate(shots, striker, target, pocket, virtualGhost, blockers, bounds, 1, physics,
                        virtualPocket = m)
                }
            }
        }
        return shots.sortedByDescending { it.score }.take(12)
    }

    private fun addCandidate(out: MutableList<Shot>, striker: Disc, target: Disc, pocket: Vec2,
                             ghost: Vec2, blockers: List<Disc>, bounds: BoardRect, bank: Int,
                             physics: PhysicsConfig, virtualPocket: Vec2 = pocket) {
        if (!bounds.contains(ghost, striker.radius) || !bounds.contains(target.center, target.radius)) return
        val aim = (ghost - striker.center)
        val coinLine = (virtualPocket - target.center)
        if (aim.length() < 1.0 || coinLine.length() < 1.0) return
        val strikerPath = listOf(striker.center, ghost)
        val coinPath = listOf(target.center, pocket)
        val collisionAngle = CarromMath.angleBetween(aim, coinLine)
        if (collisionAngle > 78.0) return
        val blocked = blockers.any {
            it.id != target.id && it.id != striker.id &&
                CarromMath.segmentIntersectsDisc(striker.center, ghost, it, striker.radius * 0.35)
        }
        if (blocked) return
        val targetBlocked = blockers.any {
            it.id != target.id && it.id != striker.id &&
                CarromMath.segmentIntersectsDisc(target.center, pocket, it, target.radius * 0.35)
        }
        if (targetBlocked) return
        val distance = striker.center.distance(ghost) + target.center.distance(pocket)
        val anglePenalty = collisionAngle / 90.0
        val bankPenalty = bank * 0.17
        val score = (100.0 - distance / max(bounds.width, bounds.height) * 24.0 -
            anglePenalty * 38.0 - bankPenalty * 100.0).coerceIn(0.0, 100.0)
        out.add(Shot(target.id, pocket, strikerPath, coinPath, collisionAngle, distance,
            bank, score, if (bank == 0) "Direct shot" else "One-cushion bank candidate"))
    }
}
