# Managed configurations (EMM/MDM)

AVNC supports [managed configurations](https://developer.android.com/work/managed-configurations),
allowing an EMM/MDM solution to enforce settings
on managed devices.

The schema is declared in [`app_restrictions.xml`](../app/src/main/res/xml/app_restrictions.xml).
Most EMM consoles use it to show a form, with sections, lists and switches.
If your EMM only offers a key/value or JSON editor, use the keys & values documented below.
Changes pushed by the EMM are applied immediately, without restarting the app.

## How values work

- **"Defined by user"**: Every setting defaults to an empty value (or `0` for numbers),
  which leaves it to the user. Any other value is enforced: it takes precedence over the
  user preference, and the setting is disabled in AVNC, with the summary "Managed by your organization".
- **Booleans** are `choice` restrictions with values `true` / `false` (plus the empty value),
  so that "Defined by user" can be selected.
- **Sections**: Restrictions are grouped in `bundle` restrictions matching AVNC Settings screens
  (`interface`, `viewer`, `input`, `servers`). Groups are only used for presentation,
  so AVNC also accepts a flat configuration (e.g. `gesture_style` at top level).
  Keys are unique across all sections.
- **Invalid values** are ignored, as if the setting was "Defined by user".

## Preferences

Restriction keys are the same as preference keys.
Per-server settings (e.g. orientation set in a server profile) still override these global values.

### Interface (`interface`)

| Key                      | Setting in AVNC                       | Values                                  |
|--------------------------|---------------------------------------|-----------------------------------------|
| `theme`                  | Theme                                 | `system`, `dark`, `light`               |
| `bell_enabled`           | Bell                                  | `true`, `false`                         |
| `sort_server_list`       | Sort saved servers by name            | `true`, `false`                         |
| `prefer_advanced_editor` | Directly open advanced server options | `true`, `false`                         |

### Viewer (`viewer`)

| Key                  | Setting in AVNC | Values                                                     |
|----------------------|-----------------|------------------------------------------------------------|
| `viewer_orientation` | Screen orientation | `auto` (Automatic), `portrait`, `landscape`                |
| `fullscreen_display` | Fullscreen      | `true`, `false`                                            |
| `keep_screen_on`     | Keep screen ON  | `true`, `false`                                            |

### Input (`input`)

Input settings are grouped in four sub-sections, like in AVNC Settings.

Gesture actions use these values:

| Value                | Meaning                 |
|----------------------|-------------------------|
| `none`               | None                    |
| `double-click`       | Double-click            |
| `middle-click`       | Middle-click            |
| `right-click`        | Right-click             |
| `left-press`         | Long-press (left button held down) |
| `open-keyboard`      | Open keyboard           |
| `pan`                | Pan local frame         |
| `remote-scroll`      | Scroll remote content   |
| `remote-drag`        | Drag remote content     |
| `remote-drag-middle` | Drag with middle button |
| `remote-back-press`  | Back-press on server    |
| `default`            | Default (Android behavior) |

#### Gestures (`input_gestures`)

| Key                         | Setting in AVNC           | Values                                                               |
|-----------------------------|---------------------------|----------------------------------------------------------------------|
| `gesture_style`             | Gesture style             | `touchscreen`, `touchpad`                                            |
| `gesture_double_tap`        | Double tap                | `none`, `double-click`, `middle-click`, `right-click`                |
| `gesture_long_press`        | Long press                | `none`, `double-click`, `middle-click`, `right-click`, `left-press`  |
| `gesture_tap2`              | Two-finger tap            | `none`, `right-click`, `open-keyboard`                               |
| `gesture_tap3`              | Three-finger tap          | `none`, `right-click`, `middle-click`                                |
| `gesture_swipe1`            | One-finger swipe          | `none`, `pan`, `remote-scroll`, `remote-drag`                        |
| `gesture_swipe2`            | Two-finger swipe          | `none`, `pan`, `remote-scroll`                                       |
| `gesture_swipe3`            | Three-finger swipe        | `none`, `pan`, `remote-scroll`                                       |
| `gesture_double_tap_swipe`  | Double tap and swipe      | `none`, `remote-drag`, `remote-drag-middle`, `pan`, `remote-scroll`  |
| `gesture_long_press_swipe`  | Long press and swipe      | `none`, `remote-drag`, `remote-drag-middle`, `pan`, `remote-scroll`  |
| `gesture_swipe_sensitivity` | Swipe sensitivity         | Integer from `5` to `15` (`10` is the AVNC default). `0` = defined by user |
| `invert_vertical_scrolling` | Invert vertical scrolling | `true`, `false`                                                      |

#### Mouse (`input_mouse`)

| Key                  | Setting in AVNC    | Values                                                         |
|----------------------|--------------------|----------------------------------------------------------------|
| `mouse_passthrough`  | Mouse passthrough  | `true`, `false`                                                |
| `capture_pointer`    | Capture pointer    | `true`, `false`                                                |
| `hide_local_cursor`  | Hide local pointer | `true`, `false`                                                |
| `hide_remote_cursor` | Hide remote pointer | `true`, `false`                                                |
| `mouse_back`         | Back button        | `default`, `middle-click`, `right-click`, `remote-back-press`  |

#### Virtual keys (`input_virtual_keys`)

| Key                            | Setting in AVNC              | Values          |
|--------------------------------|------------------------------|-----------------|
| `vk_open_with_keyboard`        | Open with keyboard           | `true`, `false` |
| `vk_use_super_with_single_tap` | Use Super key with single tap | `true`, `false` |
| `vk_row_count`                 | Rows                         | `1`, `2`, `3`   |

Custom virtual keys layout ("Customize virtual keys") is not supported, as it uses an internal format.

#### Key mapping (`input_key_mapping`)

| Key                           | Setting in AVNC        | Values          |
|-------------------------------|------------------------|-----------------|
| `km_right_alt_to_super`       | Right Alt → Super      | `true`, `false` |
| `km_language_switch_to_super` | Language Switch → Super | `true`, `false` |
| `km_back_to_escape`           | Back → Escape          | `true`, `false` |

### Servers (`servers`)

| Key                 | Setting in AVNC          | Values          |
|---------------------|--------------------------|-----------------|
| `auto_reconnect`    | Reconnect automatically  | `true`, `false` |
| `lock_saved_server` | Lock saved servers       | `true`, `false` |

## Example

This is an example in the Android Enterprise JSON format (`ManagedConfiguration` of the
Google Play EMM API), which is accepted by the JSON editor of several EMMs.
Some EMMs use their own format, but keys, types and values are always the same.

It enforces the dark theme and touchpad gestures.

```json
{
  "kind": "androidenterprise#managedConfiguration",
  "productId": "app:com.gaurav.avnc",
  "managedProperty": [
    { "key": "interface", "valueBundle": { "managedProperty": [
      { "key": "theme", "valueString": "dark" }
    ]}},
    { "key": "input", "valueBundle": { "managedProperty": [
      { "key": "input_gestures", "valueBundle": { "managedProperty": [
        { "key": "gesture_style", "valueString": "touchpad" },
        { "key": "gesture_swipe_sensitivity", "valueInteger": 12 }
      ]}}
    ]}}
  ]
}
```

## Testing

[Test DPC](https://github.com/googlesamples/android-testdpc) can be used to push managed
configurations to AVNC on a test device: *Managed configurations* → select AVNC →
*Load manifest restrictions*, then edit values and save.
Test DPC shows a generic key/value editor: sections are shown as nested bundles
("Configure" button), and values must be typed (e.g. `true`, `touchpad`).
