package io.github.mk3core.coresms

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder
import io.github.mk3core.coresms.data.MessageChanges
import io.github.mk3core.coresms.data.SearchIndex
import io.github.mk3core.coresms.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CoreSmsApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Keeps the message search index in step with the message database
        // whenever the app is running, including when a message arrives.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            MessageChanges.observe(this@CoreSmsApplication).collect { SearchIndex.sync(this@CoreSmsApplication) }
        }
    }

    // Animated GIFs play in conversations; video thumbnails are the first frame.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
