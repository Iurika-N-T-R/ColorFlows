# colorFlows

Color harmonies, palettes from pictures, and painter's shading, in a small Android app with no dependencies.

- **RVB / RYB wheels**: complementary, monochrome, analogous, analogous + accent, split complementary, triad, square, rectangle and compound harmonies. Drag any marker to turn the whole harmony. On the RYB (painter's) wheel, the complement of red is green.
- **IMAGE**: touch a picture to pick the color under your finger, or use its main colors.
- **SPHÈRE**: a lit ball of one color shows its shadow, midtone and light. Touch it to keep a shade.
- **Modifier les couleurs**: try a palette on dots, shapes, waves, gradients and flat proportions. Drag the bar to change how much of each color, and roll the dice to reshuffle.
- **Palettes**: saved on the phone. You can rename, copy the HEX values, share or delete them.
- Type any color in HEX or RGB.

Share an image to colorFlows to open it in the IMAGE tab.

## Build

```
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleRelease
```

Release builds are signed when `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) sits next to `settings.gradle.kts`. Without it they come out unsigned.

## License

MIT
