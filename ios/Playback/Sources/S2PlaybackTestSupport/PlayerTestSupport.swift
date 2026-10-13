// The app's tests reach AudioPlaybackKit's player rig through this product: a second package reference to
// AudioPlaybackKit in project.yml would give the test bundle its own copy of the package.
@_exported import PlaybackNowPlayingTestSupport
@_exported import PlaybackPlayerTestSupport
@_exported import PlaybackRenderTestSupport
