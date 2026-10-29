# Network Lens artwork

The approved concept is option 1 with its longer handle, recorded in `network-lens-approved-mockup.png`. It shows a mint Wi-Fi signal inside a lens against a full teal background. Native vector layers provide clean rendering and a dedicated monochrome silhouette; their grid and stroke geometry derive from that concept. The imperfect transparent raster extraction was discarded.

The authoritative Android layers are `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`. Both launcher attributes use `mipmap/ic_launcher`. The top-left main-screen ImageView loads the same `ic_launcher_foreground` artwork with a fractional inset for the launcher’s central 72/108 viewport, without the launcher background or mask. Its 56dp frame displays the mark about 40 percent larger than the prior 40dp frame. The glyph is tinted to the current foreground accent for contrast. There is no separate radar or duplicate logo drawing, and no white background disc. Launchers choose the outer mask and can recolour themed icons. Device details retain their Back control.

The selected concept was created with the built-in image-generation tool and then implemented as native Android vectors. It is separate from the packaged Lucide device pictograms. Native render previews and clipping checks are recorded with release verification.
