import Foundation
import UIKit
import Capacitor
import Lottie

@objc(LottieSplashPlugin)
public class LottieSplashPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "LottieSplashPlugin"
    public let jsName = "LottieSplash"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "notifyAppReady", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hide", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getConfig", returnType: CAPPluginReturnPromise)
    ]

    private var splashOverlay: UIView?
    private var lottieView: LottieAnimationView?
    private var isAppReady = false
    private var hasCompletedIntro = false
    private var isDismissing = false

    // Configurações
    private var animationPath: String = "splash"
    private var backgroundColorHex: String = "#FFFFFF"
    private var introStartFrame: CGFloat = -1
    private var introEndFrame: CGFloat = -1
    private var loopMode: String = "none"
    private var loopStartFrame: CGFloat = -1
    private var loopEndFrame: CGFloat = -1
    private var speed: CGFloat = 1.0
    private var sizeDp: CGFloat = 260.0
    private var fadeDurationSec: TimeInterval = 0.4
    private var autoDismiss: Bool = false
    private var timeoutMs: Double = 12000.0

    override public func load() {
        super.load()
        parseConfiguration()

        DispatchQueue.main.async { [weak self] in
            self?.setupLottieSplash()
        }

        if timeoutMs > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + (timeoutMs / 1000.0)) { [weak self] in
                self?.dismissSplashWithFade()
            }
        }
    }

    private func parseConfiguration() {
        let config = getConfig()
        var rawAnim = config.getString("animationPath", "splash")
        if rawAnim.hasSuffix(".json") {
            rawAnim = String(rawAnim.dropLast(5))
        }
        self.animationPath = rawAnim
        self.backgroundColorHex = config.getString("backgroundColor", "#FFFFFF")
        self.speed = CGFloat(config.getDouble("speed", 1.0))
        self.sizeDp = CGFloat(config.getInt("sizeDp", 260))
        self.fadeDurationSec = Double(config.getInt("fadeDurationMs", 400)) / 1000.0
        self.autoDismiss = config.getBoolean("autoDismiss", false)
        self.timeoutMs = Double(config.getInt("timeoutMs", 12000))
        self.loopMode = config.getString("loopMode", "none")

        if let introArr = config.getArray("introFrames") as? [NSNumber], introArr.count >= 2 {
            self.introStartFrame = CGFloat(truncating: introArr[0])
            self.introEndFrame = CGFloat(truncating: introArr[1])
        }

        if let loopArr = config.getArray("loopFrames") as? [NSNumber], loopArr.count >= 2 {
            self.loopStartFrame = CGFloat(truncating: loopArr[0])
            self.loopEndFrame = CGFloat(truncating: loopArr[1])
            if self.loopMode == "none" {
                self.loopMode = "range"
            }
        }
    }

    private func setupLottieSplash() {
        guard let parentView = bridge?.viewController?.view else { return }
        if splashOverlay != nil { return }

        let overlay = UIView(frame: parentView.bounds)
        overlay.backgroundColor = colorFromHex(backgroundColorHex)
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.isUserInteractionEnabled = true

        let animation = LottieAnimationView(name: animationPath)
        animation.contentMode = .scaleAspectFit
        animation.animationSpeed = speed

        let size: CGFloat = sizeDp
        animation.frame = CGRect(
            x: (parentView.bounds.width - size) / 2.0,
            y: (parentView.bounds.height - size) / 2.0,
            width: size,
            height: size
        )
        animation.autoresizingMask = [
            .flexibleTopMargin,
            .flexibleBottomMargin,
            .flexibleLeftMargin,
            .flexibleRightMargin
        ]

        overlay.addSubview(animation)
        parentView.addSubview(overlay)

        self.splashOverlay = overlay
        self.lottieView = animation

        let hasIntro = (introStartFrame >= 0 && introEndFrame > introStartFrame)

        if hasIntro {
            animation.play(fromFrame: introStartFrame, toFrame: introEndFrame, loopMode: .playOnce) { [weak self] completed in
                guard let self = self, !self.isDismissing else { return }
                self.hasCompletedIntro = true

                if self.isAppReady || self.autoDismiss {
                    self.dismissSplashWithFade()
                } else {
                    if self.loopMode == "range" && self.loopStartFrame >= 0 && self.loopEndFrame > self.loopStartFrame {
                        self.lottieView?.play(fromFrame: self.loopStartFrame, toFrame: self.loopEndFrame, loopMode: .loop, completion: nil)
                    } else if self.loopMode == "full" {
                        self.lottieView?.play(loopMode: .loop, completion: nil)
                    }
                }
            }
        } else {
            self.hasCompletedIntro = true
            if loopMode == "range" && loopStartFrame >= 0 && loopEndFrame > loopStartFrame {
                animation.play(fromFrame: loopStartFrame, toFrame: loopEndFrame, loopMode: .loop, completion: nil)
            } else if loopMode == "full" {
                animation.play(loopMode: .loop, completion: nil)
            } else {
                animation.play(loopMode: .playOnce) { [weak self] _ in
                    if self?.autoDismiss == true {
                        self?.dismissSplashWithFade()
                    }
                }
            }
        }
    }

    @objc func notifyAppReady(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.isAppReady = true
            if self.hasCompletedIntro {
                self.dismissSplashWithFade()
            }
        }
        call.resolve()
    }

    @objc func hide(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.dismissSplashWithFade()
        }
        call.resolve()
    }

    @objc func show(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.isDismissing = false
            self.hasCompletedIntro = false
            self.isAppReady = false
            self.setupLottieSplash()
        }
        call.resolve()
    }

    @objc func getConfig(_ call: CAPPluginCall) {
        call.resolve([
            "animationPath": animationPath,
            "backgroundColor": backgroundColorHex,
            "loopMode": loopMode,
            "speed": Double(speed),
            "sizeDp": Double(sizeDp),
            "fadeDurationMs": Int(fadeDurationSec * 1000.0),
            "autoDismiss": autoDismiss,
            "timeoutMs": timeoutMs
        ])
    }

    private func dismissSplashWithFade() {
        guard !isDismissing, let overlay = splashOverlay else { return }
        isDismissing = true

        DispatchQueue.main.async { [weak self] in
            UIView.animate(withDuration: self?.fadeDurationSec ?? 0.4, animations: {
                overlay.alpha = 0.0
            }) { [weak self] _ in
                overlay.removeFromSuperview()
                self?.splashOverlay = nil
                self?.lottieView?.stop()
                self?.lottieView = nil
            }
        }
    }

    private func colorFromHex(_ hex: String) -> UIColor {
        var cString = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cString.hasPrefix("#") {
            cString.remove(at: cString.startIndex)
        }
        if cString.count != 6 {
            return .white
        }
        var rgbValue: UInt64 = 0
        Scanner(string: cString).scanHexInt64(&rgbValue)
        return UIColor(
            red: CGFloat((rgbValue & 0xFF0000) >> 16) / 255.0,
            green: CGFloat((rgbValue & 0x00FF00) >> 8) / 255.0,
            blue: CGFloat(rgbValue & 0x0000FF) / 255.0,
            alpha: 1.0
        )
    }
}
