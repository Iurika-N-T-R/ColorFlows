# ColorFlows

Color harmonies, palettes from pictures, and painter's shading, in a small Android app with no dependencies.

- **RVB / RYB wheels**: complementary, monochrome, analogous, analogous + accent, split complementary, triad, square, rectangle and compound harmonies. Drag any marker to turn the whole harmony. On the RYB (painter's) wheel, the complement of red is green. In monochrome, each color moves on its own and the slider sets the value of the one you touched.
- **IMAGE**: touch a picture to pick the color under your finger, or use its main colors. Palettes take as many colors as you like.
- **SPHÈRE**: a ball painted with three colors of your own (shadow, midtone, light): tap one and set it with the sliders, or type the midtone and let "Auto" work out the other two. Touch the ball to pick a shade, then "Ajouter" keeps it.
- **Modifier les couleurs**: try a palette on dots, shapes, waves, gradients and flat proportions. Drag the bar to change how much of each color, and roll the dice to reshuffle.
- **Palettes**: saved on the phone. You can rename, copy the HEX values, share or delete them.
- Type any color in HEX or RGB.

Share an image to ColorFlows to open it in the IMAGE tab.

## Build

```
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleRelease
```

Release builds are signed when `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) sits next to `settings.gradle.kts`. Without it they come out unsigned.

## License

MIT
