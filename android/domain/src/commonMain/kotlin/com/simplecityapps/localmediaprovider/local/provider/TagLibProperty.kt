package com.simplecityapps.localmediaprovider.local.provider

enum class TagLibProperty(val key: String) {
    Title("TITLE"),
    Artist("ARTIST"),
    Album("ALBUM"),
    AlbumArtist("ALBUMARTIST"),
    Date("DATE"),
    Track("TRACKNUMBER"),
    Disc("DISCNUMBER"),
    Genre("GENRE"),
    OriginalDate("ORIGINALDATE"),
    Year("YEAR"),
    ReplayGainTrack("REPLAYGAIN_TRACK_GAIN"),
    ReplayGainAlbum("REPLAYGAIN_ALBUM_GAIN"),
    Lyrics("LYRICS"),
    Grouping("GROUPING"),

    // TagLib's unified names, whatever the container: ARTISTS and ALBUMARTISTS are Vorbis comments, ID3 TXXX frames or
    // MP4 freeform atoms of that name; COMPILATION is also ID3 TCMP and MP4 cpil. The MusicBrainz ids are Vorbis
    // comments, ID3 TXXX "MusicBrainz ... Id" frames (the recording id is the UFID frame) and MP4 freeform
    // "----:com.apple.iTunes:MusicBrainz ... Id" atoms.
    Artists("ARTISTS"),
    AlbumArtists("ALBUMARTISTS"),
    Compilation("COMPILATION"),
    MusicBrainzTrackId("MUSICBRAINZ_TRACKID"),
    MusicBrainzAlbumId("MUSICBRAINZ_ALBUMID"),
    MusicBrainzReleaseGroupId("MUSICBRAINZ_RELEASEGROUPID"),
    MusicBrainzArtistId("MUSICBRAINZ_ARTISTID"),
    MusicBrainzAlbumArtistId("MUSICBRAINZ_ALBUMARTISTID"),

    // Classical metadata (#1065), TagLib's unified names. ID3 maps TCOM, TSOC, TPE3, TIT1 (the work, not the grouping:
    // GRP1 is GROUPING), TIPL's arranger, MVNM and MVIN; MP4 maps ©wrt, soco, ©wrk, ©mvn, ©mvi and shwm. Vorbis comments
    // pass through, with MOVEMENT for the number and MOVEMENTTOTAL for the count; [FileTags] reads those spellings too.
    Composer("COMPOSER"),
    ComposerSort("COMPOSERSORT"),
    Conductor("CONDUCTOR"),
    Arranger("ARRANGER"),
    Work("WORK"),
    MovementName("MOVEMENTNAME"),
    MovementNumber("MOVEMENTNUMBER"),
    MovementTotal("MOVEMENTTOTAL"),
    ShowMovement("SHOWMOVEMENT"),

    // A Vorbis comment holds "Name (instrument)"; ID3's TMCL is "PERFORMER:<instrument>" with the name as the value.
    Performer("PERFORMER")
}
