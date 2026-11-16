package io.github.mk3core.coresms.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiphyTest {

    private val sample = """
        {"data":[{"id":"abc","title":"table flip","images":{
          "original":{"url":"https://media.giphy.com/o.gif","size":"2400000","width":"480","height":"270"},
          "downsized":{"url":"https://media.giphy.com/d.gif","size":"900000","width":"400","height":"225"},
          "fixed_width":{"url":"https://media.giphy.com/fw.gif","size":"500000","width":"200","height":"113"},
          "fixed_width_downsampled":{"url":"https://media.giphy.com/fwd.gif","size":"120000","width":"200","height":"113"}
        }}],
        "pagination":{"total_count":100,"count":1,"offset":0}}
    """.trimIndent()

    @Test fun parsesGifsAndNextPage() {
        val page = Giphy.parse(sample)
        assertEquals(1, page.gifs.size)
        assertEquals("https://media.giphy.com/fwd.gif", page.gifs[0].preview.url)
        assertEquals(4, page.gifs[0].renditions.size)
        assertEquals(1, page.nextOffset)
    }

    @Test fun picksLargestThatFits() {
        val gif = Giphy.parse(sample).gifs[0]
        assertEquals("https://media.giphy.com/d.gif", Giphy.bestFit(gif, 1_200_000)?.url)
        assertEquals("https://media.giphy.com/fwd.gif", Giphy.bestFit(gif, 200_000)?.url)
        assertNull(Giphy.bestFit(gif, 50_000))
    }
}
