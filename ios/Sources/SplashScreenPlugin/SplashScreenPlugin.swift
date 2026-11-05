import Foundation
import UIKit
import Capacitor
import Lottie

@objc(LaylaSplashScreen)
public class SplashScreenPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "LaylaSplashScreen"
    public let jsName = "LaylaSplashScreen"
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

    // Configurações globais
    private var backgroundColorHex: String = "#FFFFFF"
    private var iconPath: String?
    private var fadeDurationSec: TimeInterval = 0.4
    private var autoDismiss: Bool = false
    private var timeoutMs: Double = 12000.0
    private var immersive: Bool = true
    private var previousStatusBarStyle: UIStatusBarStyle?

    // Configurações de animação (opcional)
    private var hasAnimation: Bool = false
    private var animationPath: String = ""
    private var introStartFrame: CGFloat = -1
    private var introEndFrame: CGFloat = -1
    private var loopMode: String = "none"
    private var loopStartFrame: CGFloat = -1
    private var loopEndFrame: CGFloat = -1
    private var speed: CGFloat = 1.0
    private var sizeDp: CGFloat = 260.0

    override public func load() {
        super.load()
        parseConfiguration()

        DispatchQueue.main.async { [weak self] in
            self?.setupSplash()
        }

        if timeoutMs > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + (timeoutMs / 1000.0)) { [weak self] in
                self?.dismissSplashWithFade()
            }
        }
    }

    private func parseConfiguration() {
        let config = getConfig()
        self.backgroundColorHex = config.getString("backgroundColor", "#FFFFFF")
        self.iconPath = config.getString("iconPath", nil)
        self.fadeDurationSec = Double(config.getInt("fadeDurationMs", 400)) / 1000.0
        self.autoDismiss = config.getBoolean("autoDismiss", false)
        self.timeoutMs = Double(config.getInt("timeoutMs", 12000))
        self.immersive = config.getBoolean("immersive", true)

        // 1. Suporte ao subdicionário estruturado "animation"
        if let animDict = config.getObject("animation") {
            self.hasAnimation = true
            var rawAnim = animDict["path"] as? String ?? "splash"
            if rawAnim.hasSuffix(".json") {
                rawAnim = String(rawAnim.dropLast(5))
            }
            self.animationPath = rawAnim
            self.speed = CGFloat(animDict["speed"] as? Double ?? 1.0)
            self.sizeDp = CGFloat(animDict["sizeDp"] as? Int ?? 260)
            self.loopMode = animDict["loopMode"] as? String ?? "none"

            if let introArr = animDict["introFrames"] as? [NSNumber], introArr.count >= 2 {
                self.introStartFrame = CGFloat(truncating: introArr[0])
                self.introEndFrame = CGFloat(truncating: introArr[1])
            }

            if let loopArr = animDict["loopFrames"] as? [NSNumber], loopArr.count >= 2 {
                self.loopStartFrame = CGFloat(truncating: loopArr[0])
                self.loopEndFrame = CGFloat(truncating: loopArr[1])
                if self.loopMode == "none" {
                    self.loopMode = "range"
                }
            }
        } else if let rawAnimRoot = config.getString("animationPath") {
            // 2. Retrocompatibilidade: campos na raiz
            self.hasAnimation = true
            var rawAnim = rawAnimRoot
            if rawAnim.hasSuffix(".json") {
                rawAnim = String(rawAnim.dropLast(5))
            }
            self.animationPath = rawAnim
            self.speed = CGFloat(config.getDouble("speed", 1.0))
            self.sizeDp = CGFloat(config.getInt("sizeDp", 260))
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
        } else {
            self.hasAnimation = false
        }
    }

    private func setupSplash() {
        guard let parentView = bridge?.viewController?.view else { return }
        if splashOverlay != nil { return }

        // Suporte imersivo: ancora na window para cobrir Dynamic Island/Notch e barras do sistema
        var targetContainer: UIView = parentView
        if immersive {
            if let window = parentView.window ?? UIApplication.shared.windows.first(where: { $0.isKeyWindow }) {
                targetContainer = window
            }
        }

        let bgColor = colorFromHex(backgroundColorHex)
        let overlay = UIView(frame: targetContainer.bounds)
        overlay.backgroundColor = bgColor
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.isUserInteractionEnabled = true

        // Ajusta status bar style imersivo com base na luminosidade do fundo
        if immersive {
            self.previousStatusBarStyle = UIApplication.shared.statusBarStyle
            let isDarkBg = isColorDark(bgColor)
            if #available(iOS 13.0, *) {
                UIApplication.shared.setStatusBarStyle(isDarkBg ? .lightContent : .darkContent, animated: true)
            } else {
                UIApplication.shared.setStatusBarStyle(isDarkBg ? .lightContent : .default, animated: true)
            }
        }

        if hasAnimation && !animationPath.isEmpty {
            let animation = LottieAnimationView(name: animationPath)
            animation.contentMode = .scaleAspectFit
            animation.animationSpeed = speed

            let size: CGFloat = sizeDp
            animation.frame = CGRect(
                x: (targetContainer.bounds.width - size) / 2.0,
                y: (targetContainer.bounds.height - size) / 2.0,
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
            targetContainer.addSubview(overlay)

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
        } else {
            // Modo estático
            self.hasCompletedIntro = true
            targetContainer.addSubview(overlay)
            self.splashOverlay = overlay
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
            self.setupSplash()
        }
        call.resolve()
    }

    @objc func getConfig(_ call: CAPPluginCall) {
        var ret: [String: Any] = [
            "backgroundColor": backgroundColorHex,
            "fadeDurationMs": Int(fadeDurationSec * 1000.0),
            "autoDismiss": autoDismiss,
            "timeoutMs": timeoutMs,
            "immersive": immersive
        ]
        if let icon = iconPath {
            ret["iconPath"] = icon
        }
        if hasAnimation {
            ret["animation"] = [
                "path": animationPath,
                "speed": Double(speed),
                "sizeDp": Double(sizeDp),
                "loopMode": loopMode
            ]
        }
        call.resolve(ret)
    }

    private func dismissSplashWithFade() {
        guard !isDismissing, let overlay = splashOverlay else { return }
        isDismissing = true

        DispatchQueue.main.async { [weak self] in
            // Restaura o estilo do status bar se foi alterado pelo modo imersivo
            if let prevStyle = self?.previousStatusBarStyle {
                UIApplication.shared.setStatusBarStyle(prevStyle, animated: true)
                self?.previousStatusBarStyle = nil
            }

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

    private func isColorDark(_ color: UIColor) -> Bool {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        let luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance < 0.5
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

