package zed.rainxch.home.domain.model

enum class TopicCategory(
    val matchTopics: Set<String>,
    val searchKeywords: String,
) {
    PRIVACY(
        matchTopics = setOf(
            "privacy", "security", "encryption", "vpn", "firewall",
            "password-manager", "privacy-tools", "e2ee", "secure",
            "anonymity", "tor", "pgp", "2fa", "auth",
        ),
        searchKeywords = "privacy security encryption vpn firewall",
    ),
    MEDIA(
        matchTopics = setOf(
            "music-player", "video-player", "media", "podcast", "streaming",
            "audio", "video", "media-player", "music", "player",
            "mpv", "vlc", "recorder", "screen-recorder", "gallery",
        ),
        searchKeywords = "music-player video-player media podcast audio",
    ),
    PRODUCTIVITY(
        matchTopics = setOf(
            "productivity", "file-manager", "notes", "launcher", "keyboard",
            "browser", "calendar", "todo", "note-taking", "editor",
            "organizer", "task-manager", "markdown", "writing",
        ),
        searchKeywords = "productivity file-manager notes launcher browser",
    ),
    NETWORKING(
        matchTopics = setOf(
            "proxy", "dns", "ad-blocker", "torrent", "downloader",
            "network", "ssh", "wireguard", "adblock", "download-manager",
            "firewall", "socks5", "http-proxy", "p2p", "ftp",
        ),
        searchKeywords = "proxy dns ad-blocker torrent downloader network",
    ),
    DEV_TOOLS(
        matchTopics = setOf(
            "terminal", "developer-tools", "git-client", "editor", "cli",
            "ide", "devtools", "code-editor", "terminal-emulator", "development",
            "adb", "debugger", "api-client", "shell", "sdk",
        ),
        searchKeywords = "terminal developer-tools git-client code-editor cli",
    ),
    ;

    fun matchesRepo(
        topics: List<String>?,
        description: String?,
        name: String?,
    ): Boolean {
        val repoTopics = topics?.map { it.lowercase() }.orEmpty()
        if (repoTopics.any { it in matchTopics }) return true

        val desc = description?.lowercase() ?: ""
        val repoName = name?.lowercase() ?: ""
        val keywords = searchKeywords.split(" ")
        return keywords.any { keyword ->
            desc.contains(keyword) || repoName.contains(keyword)
        }
    }
}
