package io.github.mk3core.coresms

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import io.github.mk3core.coresms.notify.Notifications

class CoreSmsApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    // Video thumbnails in conversations come from the first frame of the video.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
