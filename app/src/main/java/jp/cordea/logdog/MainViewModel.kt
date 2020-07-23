package jp.cordea.logdog

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
class MainViewModel @ViewModelInject constructor() : ViewModel() {

    init {
        val logcat = Runtime.getRuntime().exec(arrayOf("logcat"))
        viewModelScope.launch {
            callbackFlow {
                val reader = logcat.inputStream.bufferedReader()
                reader.lineSequence().forEach { send(it) }
                awaitClose { reader.close() }
            }.flowOn(Dispatchers.IO).collect {
                text.value += "\n$it"
            }
        }
    }

    val text = MutableLiveData("")

    fun onLogAdditionClick() {
    }
}
