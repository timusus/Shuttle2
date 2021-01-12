package au.com.simplecityapps.shuttle.imageloading.coil.mapper

import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.map.Mapper
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class AlbumArtistUrlMapper(private val preferenceManager: GeneralPreferenceManager) : Mapper<AlbumArtist, String> {

    override fun handles(data: AlbumArtist): Boolean {
        return !preferenceManager.artworkLocalOnly
    }

    override fun map(data: AlbumArtist): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${data.name.encode()}"
    }
}