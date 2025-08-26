package com.example.effect

interface Ref<T> {
    val value: T
}

interface MutableRef<T> : Ref<T> {
    override var value: T
}

abstract class BaseRef<T> : Ref<T> {
    var effectDepTail: Dep? = null

    open fun onInactive() {}

    protected fun track() {
        val effect = Effect.Current ?: return

        var effectDep = findEffectDep(effect)
        if (effectDep == null) {
            effectDep = Dep(this, effect)
            effectDep.link()
        }
        effectDep.isOutDate = false
    }

    private fun findEffectDep(targetEffect: Effect): Dep? {
        var current = effectDepTail
        while (current != null) {
            if (current.effect == targetEffect) return current
            current = current.effectDepPrevious
        }
        return null
    }
}

abstract class Effect : AutoCloseable, Runnable {
    companion object {
        var Current: Effect? = null
        var PendingTail: Effect? = null
        var batchCount: Int = 0
    }

    var batchPrevious: Effect? = null
    var batchNext: Effect? = null
    var refDepTail: Dep? = null

    private var isRunning: Boolean = false

    override fun run() {
        if (isRunning) return

        var currentDep = refDepTail
        while (currentDep != null) {
            val node = currentDep
            currentDep = currentDep.refDepPrevious
            node.isOutDate = true
        }

        val previous = Current
        isRunning = true
        try {
            Current = this
            doRun()
        } finally {
            Current = previous
            isRunning = false

            currentDep = refDepTail
            while (currentDep != null) {
                val node = currentDep
                currentDep = currentDep.refDepPrevious
                if (node.isOutDate) {
                    node.unlink()
                }
            }
        }
    }

    override fun close() {
        var current = refDepTail
        while (current != null) {
            val node = current
            current = current.refDepPrevious
            node.unlink()
        }
    }

    protected abstract fun doRun()
    abstract fun prepare()
}

class WatchEffect(
    private var parent: WatchEffect? = null,
    private val runnable: () -> Unit
) : Effect() {

    @Volatile
    var isClosed: Boolean = false
        private set
    private var childPrevious: WatchEffect? = null
    var childTail: WatchEffect? = null

    init {
        childPrevious = parent?.childTail
        parent?.childTail = this
    }

    fun start() {
        if (!isClosed) run()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        clearChildrenEffects()

        parent?.let { p ->
            if (p.childTail == this) {
                p.childTail = childPrevious
            }
        }
        parent = null
        super.close()
    }

    private fun clearChildrenEffects() {
        var current = childTail
        while (current != null) {
            val node = current
            current = current.childPrevious
            node.childPrevious = null
            node.close()
        }
        childTail = null
    }

    override fun run() {
        if (isClosed) return
        clearChildrenEffects()
        super.run()
    }

    override fun doRun() {
        runnable()
    }

    override fun prepare() {
        if (isClosed) return

        var next = PendingTail
        while (next != null) {
            val node = next
            if (node == this) return
            next = next.batchPrevious

            var currentParent = parent
            while (currentParent != null) {
                if (currentParent == node) return
                currentParent = currentParent.parent
            }
            var currentChild = childTail

            while (currentChild != null) {
                if (currentChild == node) {
                    val prevNode = node.batchPrevious
                    val nextNode = node.batchNext
                    prevNode?.batchNext = nextNode
                    nextNode?.batchPrevious = prevNode
                    node.batchPrevious = null
                    node.batchNext = null
                    break
                }
                currentChild = currentChild.childPrevious
            }
        }

        batchPrevious = PendingTail
        PendingTail?.batchNext = this
        PendingTail = this
    }
}

class Dep(
    private var ref: BaseRef<*>?,
    var effect: Effect? = null
) {
    var isOutDate: Boolean = false

    var refDepPrevious: Dep? = null
    private var refDepNext: Dep? = null

    var effectDepPrevious: Dep? = null
    private var effectDepNext: Dep? = null

    fun link() {
        val currentRef = ref ?: return
        val currentEffect = effect ?: return

        effectDepPrevious = currentRef.effectDepTail
        currentRef.effectDepTail?.effectDepNext = this
        currentRef.effectDepTail = this

        refDepPrevious = currentEffect.refDepTail
        currentEffect.refDepTail?.refDepNext = this
        currentEffect.refDepTail = this
    }

    fun unlink() {
        val currentRef = ref
        val currentEffect = effect

        if (currentRef != null) {
            unlinkFromChain(
                isTail = { currentRef.effectDepTail == this },
                setTail = { currentRef.effectDepTail = it },
                previous = effectDepPrevious,
                next = effectDepNext,
                setPrevNext = { it.effectDepNext = effectDepNext },
                setNextPrev = { it.effectDepPrevious = effectDepPrevious }
            )

            if (currentRef.effectDepTail == null) {
                currentRef.onInactive()
            }
        }

        if (currentEffect != null) {
            unlinkFromChain(
                isTail = { currentEffect.refDepTail == this },
                setTail = { currentEffect.refDepTail = it },
                previous = refDepPrevious,
                next = refDepNext,
                setPrevNext = { it.refDepNext = refDepNext },
                setNextPrev = { it.refDepPrevious = refDepPrevious }
            )
        }
    }

    private inline fun unlinkFromChain(
        isTail: () -> Boolean,
        setTail: (Dep?) -> Unit,
        previous: Dep?,
        next: Dep?,
        setPrevNext: (Dep) -> Unit,
        setNextPrev: (Dep) -> Unit
    ) {
        if (isTail()) {
            setTail(previous)
        }
        previous?.let(setPrevNext)
        next?.let(setNextPrev)
    }
}

open class RefImpl<T>(private var mValue: T) : BaseRef<T>(), MutableRef<T> {
    override var value: T
        get() {
            track()
            return mValue
        }
        set(newValue) {
            if (mValue == newValue) return
            batch {
                mValue = newValue
                notifyDependents()
            }
        }

    private fun notifyDependents() {
        var current = effectDepTail
        while (current != null) {
            val effectNode = current
            current = current.effectDepPrevious
            effectNode.effect?.prepare()
        }
    }
}

class ComputedRefImpl<T>(
    private val compute: () -> T
) : BaseRef<T>() {
    private var isDirty = true
    private var mValue: T? = null

    override val value: T
        get() {
            track()
            if (isDirty) {
                isDirty = false
                effect.run()
            }
            return requireNotNull(mValue)
        }

    private val effect = object : Effect() {
        override fun close() {
            isDirty = true
            mValue = null
            super.close()
        }

        override fun doRun() {
            mValue = compute()
        }

        override fun prepare() {
            isDirty = true
            var current = effectDepTail
            while (current != null) {
                val node = current
                current = current.effectDepPrevious
                node.effect?.prepare()
            }
        }
    }

    override fun onInactive() {
        effect.close()
    }
}

fun <T> ref(def: T): MutableRef<T> = RefImpl(def)

fun <T> computed(compute: () -> T): Ref<T> = ComputedRefImpl(compute)

fun runEffect(block: () -> Unit): AutoCloseable {
    val current = Effect.Current
    val eff = WatchEffect(current as? WatchEffect, block)
    eff.start()
    return eff
}

fun batch(block: () -> Unit) {

    val wasTopLevel = Effect.batchCount == 0

    try {
        Effect.batchCount++
        block()
    } finally {
        Effect.batchCount--
    }

    val shouldExecutePending = wasTopLevel && Effect.batchCount == 0
    if (!shouldExecutePending) return

    flushPending()
}

fun flushPending() {
    val current = Effect.PendingTail
    Effect.PendingTail = null

    var node = current
    while (node != null) {
        val next = node.batchPrevious
        node.run()
        node.batchPrevious = null
        node.batchNext = null
        node = next
    }
}
