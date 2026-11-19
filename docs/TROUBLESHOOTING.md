# Troubleshooting

## A device is missing

Check that it is powered on, awake and connected to the same network. Run a fresh scan while keeping Network Lens open. Guest Wi-Fi, client isolation, separate VLANs, VPN routing and device firewalls can prevent discovery. Do not disable protections blindly; first check whether they separate the phone from the device.

Open **Scan details** to see which methods ran and whether they reported a permission, resolution, response or time-limit problem. A device can be connected without answering the methods available to the app.

On automatic ranges larger than 1,024 addresses, Network Lens keeps service discovery but skips individual address checks. Choose a smaller range if you need those checks. IPv6 discovery follows eligible advertised addresses; it does not sweep the IPv6 address space.

## A device is unnamed or has the wrong type

Open the device and expand **Show technical details**. Look at the reported model, manufacturer, services and lookup outcomes.

- **No model reported:** try again while the device is awake. A failed lookup does not prove that the device has no model information.
- **Model reported but product unknown:** the local interpretation may not cover that value.
- **Conflicting reports:** the app avoids selecting an unsupported identity. Include both sources when reporting the issue.
- **Only an audio, casting or Matter service:** that capability may not identify the physical device.

Do Not Disturb is not enough evidence to explain a missing identity. Power state, local service availability and the actual response matter. Test awake and sleeping states separately rather than assuming the cause.

## The MAC address is unavailable

Modern Android restricts access to other devices' MAC addresses. Network Lens shows a MAC only when a supported source supplies one. A private/randomised address may not identify the manufacturer. HomeKit pairing IDs are not hardware MACs.

## The scan finished, but a method reported a problem

**Scan finished** describes the scan lifecycle. It does not mean every method succeeded or every device was found. Method-specific failures and incomplete work remain in **Scan details**. **Scan stopped** means the scan was interrupted rather than finishing normally.

## Android will not install the update

Use **Check for updates** and follow Android's installation prompt. If install permission is needed, allow installs from Network Lens, return and tap **Install update** again. Check free storage and any device-management restrictions.

Public releases share one signing identity and should update in place. A development build signed with another key cannot be updated by the public APK. Do not uninstall merely to retry; first confirm which build is installed. Never bypass a signature-verification failure or install an APK from an unrelated source.

If the download fails, retry on a working internet connection. Update checks use the phone's normal internet route; recognition itself stays local. Very old app versions without the updater need the [GitHub download](https://github.com/kazands/network-lens/releases) once.

## Report a recognition problem

For device-level investigation, use **Scan details → Troubleshooting report**. It includes every device and address, including unnamed devices, returned evidence and recorded lookup history. Preview it, then choose **Share full report** to send one text attachment through an app you select. Names and addresses are masked by default; enable their inclusion only when needed for private support. [What is included, withheld or not retained](reference/TROUBLESHOOTING-REPORT.md).


After a scan finishes or is stopped, open **Scan details → Copy recognition report** and paste the summary into your support conversation. It includes aggregate recognition counts and lookup outcomes, with no device names, addresses, model strings, serials or credentials. It does not upload anything. Sample results and scans still in progress cannot be copied this way.

The report describes what the app recognised, not whether every identification is correct. It counts device groups separately from address endpoints and excludes this phone's groups. A missing lookup record is not proof that no request ran: some readers report only scan-wide outcomes. For an individual problem, the information below is still useful.

Include the Network Lens version, Android version, expected make/model and whether the device was awake. From technical details, share only the relevant reported model, manufacturer, device type, services and lookup outcome/source.

Remove IP/MAC addresses, room and personal names, SSIDs, serials, pairing IDs and credentials before posting. Never attach an unredacted household scan or screenshot to a public issue. Use synthetic examples when possible.

[Open an issue](https://github.com/kazands/network-lens/issues) · [Security reporting](../SECURITY.md) · [Documentation home](README.md)
