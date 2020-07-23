package jp.cordea.logdog

import android.util.Log
import dagger.Reusable
import javax.inject.Inject

@Reusable
class Logger @Inject constructor() {
    fun info(tag: String, message: String) = Log.i(tag, message)
}
