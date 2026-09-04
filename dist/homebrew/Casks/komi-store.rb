cask "komi-store" do
  arch arm: "arm64", intel: "x64"

  version "1.9.2"
  sha256 arm:   "df371a7c4c821125810dacaab04a7fb0bcd40162bc996b798caf21c586fd0f0f",
         intel: "2d95aa11a273528978cb2dad427e592d0705bb8e67c099a91a3216677abc4315"

  url "https://github.com/komi-store/komi-store/releases/download/v#{version}/Komi-Store-#{version}-#{arch}.dmg"
  name "Komi Store"
  desc "Cross-platform app store for GitHub releases"
  homepage "https://github.com/komi-store/komi-store"

  livecheck do
    url :url
    strategy :github_latest
  end

  auto_updates false
  depends_on macos: :big_sur

  app "Komi-Store.app"

  uninstall quit: "zed.rainxch.githubstore"

  zap trash: [
    "~/Library/Application Support/Komi-Store",
    "~/Library/Caches/Komi-Store",
    "~/Library/Logs/Komi-Store",
    "~/Library/Preferences/zed.rainxch.githubstore.plist",
    "~/Library/Saved Application State/zed.rainxch.githubstore.savedState",
  ]

  caveats <<~EOS
    Komi Store is not yet signed with an Apple Developer ID.
    macOS Gatekeeper will block it from launching with a "damaged" or
    "cannot be opened" error.

    To allow the app to launch, run:

      xattr -dr com.apple.quarantine "#{appdir}/Komi-Store.app"

    This step is required after each install or upgrade until the app is
    signed and notarized.
  EOS
end
