# M2 content check (Story 2.17, SM-4)

Proof that all 147 steps have working text, audio and visuals on the device, plus a manual run of all 15 tasks for both character variants.

## Automated check

With the phone or tablet connected:

```
cd android
./gradlew connectedDebugAndroidTest
```

`ContentCompletenessTest` (`android/app/src/androidTest/java/com/aitherapist/dls/catalog/`) loads the shipped catalogue and:

- `everyListedAssetExistsAndDecodes` opens every still frame, demonstration clip (WebP via `ImageDecoder`) and normal and slow audio file (mp3 via `MediaExtractor`, duration > 0) the catalogue lists, for every step and both variants. All failures are reported together as `step id [variant] asset path: error`.
- `completeCatalogueHasNoMissingMediaOrAudio` is the M2 gate. While `assets/catalog/version.json` says `complete: false` it is skipped, and the skip message gives the number of steps missing audio and visuals per variant. Once the catalogue is `complete: true` it fails on any step without audio or visuals.

## Manual run

Run each task once per variant (a child profile with the boy character, one with the girl). Columns: all steps shown (every step appears with text and still frame or clip), audio heard (normal audio on each step, slow audio on Help), demonstration clip from attempt 2, end screen reached.

| Task | Variant | All steps shown | Audio heard | Demonstration clip from attempt 2 | End screen | Pass | Notes |
|---|---|---|---|---|---|---|---|
| `eat.sandwich` | boy | | | | | | |
| `eat.sandwich` | girl | | | | | | |
| `eat.spoon` | boy | | | | | | |
| `eat.spoon` | girl | | | | | | |
| `eat.fork` | boy | | | | | | |
| `eat.fork` | girl | | | | | | |
| `drink.cup` | boy | | | | | | |
| `drink.cup` | girl | | | | | | |
| `drink.bottle` | boy | | | | | | |
| `drink.bottle` | girl | | | | | | |
| `drink.straw` | boy | | | | | | |
| `drink.straw` | girl | | | | | | |
| `hygiene.handwash` | boy | | | | | | |
| `hygiene.handwash` | girl | | | | | | |
| `hygiene.teeth` | boy | | | | | | |
| `hygiene.teeth` | girl | | | | | | |
| `dress.jacket.on` | boy | | | | | | |
| `dress.jacket.on` | girl | | | | | | |
| `dress.jacket.off` | boy | | | | | | |
| `dress.jacket.off` | girl | | | | | | |
| `dress.tshirt.on` | boy | | | | | | |
| `dress.tshirt.on` | girl | | | | | | |
| `dress.shirt.on` | boy | | | | | | |
| `dress.shirt.on` | girl | | | | | | |
| `dress.trousers.on` | boy | | | | | | |
| `dress.trousers.on` | girl | | | | | | |
| `dress.socks.on` | boy | | | | | | |
| `dress.socks.on` | girl | | | | | | |
| `dress.shoes.on` | boy | | | | | | |
| `dress.shoes.on` | girl | | | | | | |
