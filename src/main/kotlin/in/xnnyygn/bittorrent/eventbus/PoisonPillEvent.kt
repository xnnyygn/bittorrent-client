package `in`.xnnyygn.bittorrent.eventbus

import kotlinx.coroutines.CompletableDeferred

data class PoisonPillEvent(val completableDeferred: CompletableDeferred<Unit>) : Event