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
        var effectDep = findEffectDep()
        if (effectDep == null) {
            effectDep = Dep(this, effect)
            effectDep.link()
        }
        effectDep.isOutDate = false
    }

    private fun findEffectDep(): Dep? {
        var current = effectDepTail
        val effect = Effect.Current
        while (current != null) {
            if (current.effect == effect) return current
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

    override fun run() {
        var currentDep = refDepTail
        while (currentDep != null) {
            val node = currentDep
            currentDep = currentDep.refDepPrevious
            node.isOutDate = true
        }

        val previous = Current
        try {
            Current = this
            doRun()
        } finally {
            Current = previous
            currentDep = refDepTail
            while (currentDep != null) {
                val node = currentDep
                currentDep = currentDep.refDepPrevious
                if (node.isOutDate) node.unlink()
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

    private var isClosed: Boolean = false
    var closableTail: WatchEffect? = null
    var closablePrevious: WatchEffect? = null

    init {
        closablePrevious = parent?.closableTail
        parent?.closableTail = this
    }

    fun start() {
        run()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        clearChildrenEffects()
        parent = null
        super.close()
    }

    private fun clearChildrenEffects() {
        var current = closableTail
        while (current != null) {
            val node = current
            current = current.closablePrevious
            node.closablePrevious = null
            node.close()
        }
        closableTail = null
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
        var current = PendingTail
        while (current != null) {
            if (current == this) return
            current = current.batchPrevious
        }
        batchPrevious = PendingTail
        PendingTail = this
    }
}

class Dep(
    private val ref: BaseRef<*>,
    var effect: Effect? = null
) {
    var isOutDate: Boolean = false

    var refDepPrevious: Dep? = null
    private var refDepNext: Dep? = null

    var effectDepPrevious: Dep? = null
    private var effectDepNext: Dep? = null

    fun link() {
        effectDepPrevious = ref.effectDepTail
        ref.effectDepTail?.effectDepNext = this
        ref.effectDepTail = this

        effect?.let {
            refDepPrevious = it.refDepTail
            it.refDepTail?.refDepNext = this
            it.refDepTail = this
        }
    }

    fun unlink() {
        unlinkFrom(
            isTail = { ref.effectDepTail == this },
            setTail = { ref.effectDepTail = it },
            previous = effectDepPrevious,
            next = effectDepNext,
            isRefDep = false
        )
        if (ref.effectDepTail == null) {
            ref.onInactive()
        }

        effect?.apply {
            unlinkFrom(
                isTail = { refDepTail == this@Dep },
                setTail = { refDepTail = it },
                previous = refDepPrevious,
                next = refDepNext,
                isRefDep = true
            )
        }

        effectDepPrevious = null
        effectDepNext = null
        refDepPrevious = null
        refDepNext = null
        effect = null
    }

    private fun unlinkFrom(
        isTail: () -> Boolean,
        previous: Dep?,
        next: Dep?,
        setTail: (Dep?) -> Unit,
        isRefDep: Boolean
    ) {
        if (isTail()) {
            setTail(previous)
        }
        previous?.let {
            if (isRefDep) it.refDepNext = next else it.effectDepNext = next
        }
        next?.let {
            if (isRefDep) it.refDepPrevious = previous else it.effectDepPrevious = previous
        }
    }

}

open class RefImpl<T>(def: T) : BaseRef<T>(), MutableRef<T> {
    private var mValue = def

    override var value: T
        get() {
            track()
            return mValue
        }
        set(value) {
            if (mValue == value) return
            batch {
                mValue = value
                prepare()
            }
        }


    private fun prepare() {
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
            if (isDirty) effect.run()
            return requireNotNull(mValue)
        }

    private val effect = object : Effect() {

        override fun close() {
            isDirty = true
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

        override fun run() {
            super.run()
            isDirty = false
        }
    }

    override fun onInactive() {
        effect.close()
    }

}

fun <T> ref(def: T): MutableRef<T> {
    return RefImpl(def)
}

fun <T> computed(compute: () -> T): Ref<T> {
    return ComputedRefImpl(compute)
}

fun runEffect(block: () -> Unit): AutoCloseable {
    val current = Effect.Current
    val eff = WatchEffect(current as? WatchEffect, block)
    eff.start()
    return eff
}

fun batch(block: () -> Unit) {
    try {
        Effect.batchCount++
        block()
    } finally {
        Effect.batchCount--
    }

    if (Effect.batchCount != 0) return

    var current = cleanPendingEffects(Effect.PendingTail)
    Effect.PendingTail = null

    while (current != null) {
        val node = current
        current = current.batchPrevious
        node.run()
        node.batchPrevious = null
    }
}

// Optimized cleanup function using double-linked list operations
private fun cleanPendingEffects(tail: Effect?): Effect? {
    if (tail == null) return null

    var newTail = tail
    var current = tail

    // Single pass: traverse and remove child effects on the fly
    while (current != null) {
        val next = current.batchPrevious

        if (current is WatchEffect) {
            // Remove any children of this effect from the chain
            newTail = removeChildrenFromChain(current, newTail)
        }

        current = next
    }

    return newTail
}

// Remove all children of the parent that exist in the batch chain
private fun removeChildrenFromChain(parent: WatchEffect, currentTail: Effect?): Effect? {
    var newTail = currentTail
    var child = parent.closableTail

    while (child != null) {
        // Check if this child is in the batch chain by looking for batch links
        if (child.batchPrevious != null || child.batchNext != null || child == currentTail) {
            newTail = unlinkFromBatchChain(child, newTail)
        }

        // Recursively remove grandchildren
        newTail = removeChildrenFromChain(child, newTail)

        child = child.closablePrevious
    }

    return newTail
}

// Efficiently unlink a node from the double-linked batch chain
private fun unlinkFromBatchChain(effect: Effect, currentTail: Effect?): Effect? {
    val prev = effect.batchPrevious
    val next = effect.batchNext

    // Update links of adjacent nodes
    prev?.batchNext = next
    next?.batchPrevious = prev

    // Update tail if necessary
    val newTail = if (effect == currentTail) prev else currentTail

    // Clear the removed node's links
    effect.batchPrevious = null
    effect.batchNext = null

    return newTail
}