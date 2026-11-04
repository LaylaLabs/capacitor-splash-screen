// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LaylalabsCapacitorSplashScreen",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "LaylalabsCapacitorSplashScreen",
            targets: ["SplashScreenPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0"),
        .package(url: "https://github.com/airbnb/lottie-ios.git", from: "4.4.0")
    ],
    targets: [
        .target(
            name: "SplashScreenPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Lottie", package: "lottie-ios")
            ],
            path: "ios/Sources/SplashScreenPlugin")
    ]
)
