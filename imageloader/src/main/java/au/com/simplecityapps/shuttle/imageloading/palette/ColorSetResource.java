package au.com.simplecityapps.shuttle.imageloading.palette;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.Resource;
import com.simplecity.amp_library.glide.palette.ColorSet;

import org.jetbrains.annotations.NotNull;

public class ColorSetResource implements Resource<ColorSet> {
    private final ColorSet colorSet;

    public ColorSetResource(@NonNull ColorSet colorSet) {
        this.colorSet = colorSet;
    }

    @NonNull
    @Override
    public Class<ColorSet> getResourceClass() {
        return ColorSet.class;
    }

    @NotNull
    @Override
    public ColorSet get() {
        return colorSet;
    }

    @Override
    public int getSize() {
        return ColorSet.Companion.estimatedSize();
    }

    @Override
    public void recycle() {

    }
}