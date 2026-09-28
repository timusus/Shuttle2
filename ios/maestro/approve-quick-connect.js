// Approves the Quick Connect code the sign-in screen shows (maestro.copiedText) for MAESTRO_SERVER_USER, with the
// server's API key, as another Jellyfin app would. Never logs the key or the address.
var auth = { headers: { Authorization: 'MediaBrowser Token="' + MAESTRO_API_KEY + '"' } }
var base = MAESTRO_SERVER_URL.replace(/\/+$/, '')
var name = typeof MAESTRO_SERVER_USER !== 'undefined' && MAESTRO_SERVER_USER ? MAESTRO_SERVER_USER : 'shuttle-test'

var users = json(http.get(base + '/Users', auth).body)
var user = users.filter(function (u) { return u.Name === name })[0]
if (!user) throw new Error('No Jellyfin user named ' + name)

var code = maestro.copiedText.trim()
var response = http.post(base + '/QuickConnect/Authorize?code=' + encodeURIComponent(code) + '&userId=' + user.Id, { headers: auth.headers, body: '' })
if (response.status !== 200) throw new Error('Quick Connect approval failed: HTTP ' + response.status)
output.approved = true
