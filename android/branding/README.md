# Network Lens artwork

The approved 2026-10-30 logo is the hyper-realistic Wi-Fi/lens emblem: dark teal metal, two signal arcs and a dot on a frosted mint glass disc. `network-lens-approved-mockup.png` preserves the approved studio rendering. `network-lens-hyperreal.png` is its transparent-background adaptation, made with the built-in image-generation tool. Only the studio backdrop and floor shadow were requested to be removed; the glass and metal finish remains.

The full-colour source is exported to a 512px `res/drawable-nodpi/network_lens_logo.png`. Both the 56dp main-screen header and launcher use that image without a tint. `ic_launcher_foreground.xml` gives it a proportional 16.666667 percent inset, placing the image in the central 72/108 adaptive-icon viewport. `ic_launcher_background.xml` supplies the full-bleed light mint field (`#B9F1D8`); Android launchers supply the outer mask. The studio photograph's white background is not packaged as the launcher foreground. The mint disc stays readable in light and dark headers. Device details retain Back navigation.

`ic_launcher_monochrome.xml` is the simplified two-arc Wi-Fi/lens companion used when Android themed icons recolour the mark. It deliberately omits glass shading and the backing disc so that the lens and signal remain recognisable. `network-lens.svg` retains the earlier flat Balanced artwork as an editable reference for this companion; it is not the source of the full-colour logo.

`store-listing/en-US/icon.png` is a clean 512px full-field export from the native Android renderer, without a baked launcher mask or third-party apps. It is prepared locally and has not been published to a store.

Provenance, generation prompts and artwork hashes are recorded in `provenance.json`. Network Lens artwork is separate from the packaged Lucide device pictograms. Local emulator captures are ignored and are not branding source assets.
