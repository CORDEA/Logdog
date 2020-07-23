package jp.cordea.logdog

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
                .takeMap(1000) { it.joinToString("\n") }
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

    private fun <T> Flow<T>.takeMap(count: Int, mapper: (List<T>) -> T): Flow<T> = flow {
        val list = mutableListOf<T>()
        collect { value ->
            if (list.size < count) {
                list.add(value)
            } else {
                emit(mapper(list))
                list.clear()
            }
        }
    }
}
