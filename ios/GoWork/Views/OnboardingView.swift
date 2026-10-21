import FamilyControls
import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var step = 0
    @State private var showPicker = false
    @State private var requesting = false

    private let steps = 5

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ForEach(0..<steps, id: \.self) { index in
                    Capsule()
                        .fill(index <= step ? Color.goVolt : Color.goPanelHigh)
                        .frame(width: index == step ? 28 : 10, height: 6)
                }
            }
            .padding(.top, 12)
            .animation(.easeInOut, value: step)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    switch step {
                    case 0: welcome
                    case 1: aboutYou
                    case 2: permission
                    case 3: chooseApps
                    default: rules
                    }
                }
                .padding(.top, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            footer
                .padding(.bottom, 8)
        }
        .padding(.horizontal, 22)
        .background(Color.goInk.ignoresSafeArea())
        .familyActivityPicker(isPresented: $showPicker, selection: $model.selection)
        .onAppear {
            if model.settings.onboarded { step = model.isAuthorized ? 3 : 2 }
        }
    }

    // MARK: Steps

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("⚡").font(.system(size: 54))
            Headline(text: "GO DO YOUR WORK.", size: 50)
            Text("Instagram and YouTube are built to eat your time. GoWork watches them in the background and pulls you out.")
                .font(.title3)
                .foregroundColor(.goMuted)
            GoCard {
                feature("timer", "Every 2 minutes", "A full-screen \"Go do your work\" shield. Continue only on purpose.")
                feature("hourglass", "Daily limit", "Hit it and your apps stay locked until midnight.")
                feature("chart.bar.fill", "Real Screen Time", "See exactly where your day went.")
                feature("target", "Focus hours & sprints", "Hard lock while you edit, write or study.")
            }
        }
    }

    private var aboutYou: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Who are we keeping on track?")
                .font(.title.weight(.heavy))
            Text("The shield calls you by name and reminds you what you're working on.")
                .foregroundColor(.goMuted)
            field("Your name", placeholder: "Jaivish", text: $model.settings.userName)
            field("What should you be doing instead?", placeholder: "Finish editing the client video", text: $model.settings.goal)
        }
    }

    private var permission: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Allow Screen Time access")
                .font(.title.weight(.heavy))
            Text("iOS lets GoWork shield apps and read your usage only with your Face ID / passcode approval. Nothing leaves your phone.")
                .foregroundColor(.goMuted)
            GoCard(border: model.isAuthorized ? .goMint : .goStroke) {
                Label(
                    model.isAuthorized ? "Screen Time access granted" : "Not allowed yet",
                    systemImage: model.isAuthorized ? "checkmark.seal.fill" : "exclamationmark.triangle.fill"
                )
                .foregroundColor(model.isAuthorized ? .goMint : .goVolt)
                .font(.headline)
                if !model.isAuthorized {
                    Button(requesting ? "Waiting for iOS…" : "Allow Screen Time access") {
                        requesting = true
                        Task {
                            await model.requestAuthorization()
                            requesting = false
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(requesting)
                }
            }
            if let error = model.scheduleError {
                Text(error).font(.footnote).foregroundColor(.goFlame)
            }
        }
    }

    private var chooseApps: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Pick your time-wasters")
                .font(.title.weight(.heavy))
            Text("Select Instagram and YouTube (and anything else that eats your day). iOS keeps your choice private, even from GoWork.")
                .foregroundColor(.goMuted)
            Button(model.selection.isEmptySelection ? "Choose apps" : "Change apps (\(model.selection.itemCount) selected)") {
                showPicker = true
            }
            .buttonStyle(PrimaryButtonStyle())
            if !model.selection.applicationTokens.isEmpty {
                GoCard {
                    ForEach(Array(model.selection.applicationTokens), id: \.self) { token in
                        Label(token)
                    }
                }
            }
        }
    }

    private var rules: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Your rules")
                .font(.title.weight(.heavy))
            Text("You can change these any time in Settings.")
                .foregroundColor(.goMuted)
            GoCard {
                Stepper(
                    "\"Go do your work\" every \(model.settings.nudgeIntervalMinutes) min",
                    value: $model.settings.nudgeIntervalMinutes,
                    in: 1...30
                )
                Divider().overlay(Color.goStroke)
                Stepper(
                    model.settings.dailyLimitMinutes == 0 ? "No daily limit" : "Daily limit \(model.settings.dailyLimitMinutes) min",
                    value: $model.settings.dailyLimitMinutes,
                    in: 0...240,
                    step: 5
                )
                Divider().overlay(Color.goStroke)
                Toggle("Mindful pause on every open", isOn: $model.settings.mindfulOpen)
                Toggle("Notification with each reminder", isOn: $model.settings.notifyOnNudge)
            }
        }
    }

    // MARK: Footer

    @ViewBuilder
    private var footer: some View {
        switch step {
        case 0:
            Button("Let's set it up →") { step = 1 }
                .buttonStyle(PrimaryButtonStyle())
        case 2:
            Button("Next →") { step = 3 }
                .buttonStyle(PrimaryButtonStyle(color: model.isAuthorized ? .goVolt : .goPanelHigh))
                .disabled(!model.isAuthorized)
        case 3:
            Button("Next →") { step = 4 }
                .buttonStyle(PrimaryButtonStyle(color: model.selection.isEmptySelection ? .goPanelHigh : .goVolt))
                .disabled(model.selection.isEmptySelection)
        case 4:
            Button("Start protecting me") { model.finishOnboarding() }
                .buttonStyle(PrimaryButtonStyle(color: .goMint))
        default:
            Button("Next →") { step += 1 }
                .buttonStyle(PrimaryButtonStyle())
        }
    }

    // MARK: Pieces

    private func feature(_ icon: String, _ title: String, _ body: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.goVolt)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(body).font(.subheadline).foregroundColor(.goMuted)
            }
        }
    }

    private func field(_ title: String, placeholder: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundColor(.goMuted)
            TextField(placeholder, text: text)
                .padding(14)
                .background(Color.goPanel)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Color.goStroke))
        }
    }
}
