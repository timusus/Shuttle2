package au.com.simplecityapps.shuttle.imageloading.palette;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;

public class ColorSetTranscoder implements ResourceTranscoder<Bitmap, ColorSet> {

    private final Context context;

    public ColorSetTranscoder(Context context) {
        this.context = context;
    }

    @Nullable
    @Override
    public Resource<ColorSet> transcode(@NonNull Resource<Bitmap> toTranscode, @NonNull Options options) {
        return new ColorSetResource(ColorSet.Companion.fromBitmap(context, toTranscode.get()));
    }
}