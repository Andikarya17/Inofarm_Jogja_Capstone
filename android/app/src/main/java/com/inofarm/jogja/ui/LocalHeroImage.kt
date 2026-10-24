package com.inofarm.jogja.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Two local nodpi assets. No global bitmap cache; state owns only the current viewport decode. */
internal fun decodeHero(resources: Resources, resource: Int, target: IntSize): Bitmap {
    require(target.width>0 && target.height>0)
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true;inScaled=false}
    BitmapFactory.decodeResource(resources,resource,bounds)
    var sample=1
    while(bounds.outWidth/(sample*2)>=target.width && bounds.outHeight/(sample*2)>=target.height) sample*=2
    return requireNotNull(BitmapFactory.decodeResource(resources,resource,BitmapFactory.Options().apply {
        inScaled=false;inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888
    }))
}

@Composable internal fun LocalHeroImage(resource: Int, modifier: Modifier) {
    val resources=LocalContext.current.resources
    var target by remember {mutableStateOf(IntSize.Zero)}
    key(resources,resource,target) {
        val bitmap by produceState<ImageBitmap?>(null) {
            if(target.width<=0 || target.height<=0) return@produceState
            var unowned: Bitmap?=null
            try {
                withContext(Dispatchers.IO) {unowned=decodeHero(resources,resource,target)}
                ensureActive()
                value=unowned?.asImageBitmap()
                unowned=null
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {value=null}
            finally {unowned?.recycle()}
        }
        val current=bitmap
        if(current != null) Image(bitmap=current,contentDescription=null,
            modifier=modifier.onSizeChanged {target=it},contentScale=ContentScale.Crop)
        else androidx.compose.foundation.layout.Box(modifier.onSizeChanged {target=it})
    }
}
