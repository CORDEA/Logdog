package jp.cordea.logdog

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

@ExperimentalTime
@ExperimentalCoroutinesApi
class MainViewModel @ViewModelInject constructor(
    private val logger: Logger
) : ViewModel() {
    companion object {
        private const val LOG_MAX_LINES = 500
    }

    init {
        val logcat = Runtime.getRuntime().exec(arrayOf("logcat"))
        viewModelScope.launch {
            callbackFlow {
                val reader = logcat.inputStream.bufferedReader()
                reader.lineSequence().forEach { send(it) }
                awaitClose { reader.close() }
            }
                .debounceMap(500.milliseconds) { it }
                // First log is too large.
                .drop(1)
                .map { ((texts.value ?: emptyList()) + it).takeLast(LOG_MAX_LINES) }
                .flowOn(Dispatchers.IO)
                .collect { texts.value = it }
        }
    }

    private val texts = MutableLiveData<List<String>>()
    val text = texts.map { it.joinToString("\n") }

    fun onLogAdditionClick() {
        logger.info(MainViewModel::class.java.name, "add")
    }

    private suspend fun <T : Any, R : Any> Flow<T>.debounceMap(
        timeout: Duration,
        mapper: (List<T>) -> R
    ): Flow<R> = flow {
        coroutineScope {
            val channel = produce {
                collect { value -> send(value) }
            }
            var values: MutableList<T>? = mutableListOf()
            while (values != null) {
                select<Unit> {
                    channel.onReceiveOrNull().invoke { value ->
                        if (value == null) {
                            values?.let { emit(mapper(it)) }
                            values = null
                        } else {
                            values?.add(value)
                        }
                    }
                    values?.let {
                        if (it.isNotEmpty()) {
                            onTimeout(timeout.toLongMilliseconds()) {
                                emit(mapper(it))
                                values?.clear()
                            }
                        }
                    }
                }
            }
        }
    }
}
