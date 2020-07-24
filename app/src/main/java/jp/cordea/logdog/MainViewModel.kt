package jp.cordea.logdog

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
    init {
        val logcat = Runtime.getRuntime().exec(arrayOf("logcat"))
        viewModelScope.launch {
            callbackFlow {
                val reader = logcat.inputStream.bufferedReader()
                reader.lineSequence().forEach { send(it) }
                awaitClose { reader.close() }
            }
                .debounceMap(500.milliseconds) { it.joinToString("\n") }
                .flowOn(Dispatchers.IO)
                .collect {
                    text.value += "\n$it"
                }
        }
    }

    val text = MutableLiveData("")

    fun onLogAdditionClick() {
        logger.info(MainViewModel::class.java.name, "add")
    }

    private suspend fun <T : Any> Flow<T>.debounceMap(
        timeout: Duration,
        mapper: (List<T>) -> T
    ): Flow<T> = flow {
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
