import FamilyControls
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showPicker = false
    @State private var unlockAt: Date?
    @State private var now = Date()
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        let editable = model.canEdit(now: now)
        Form {
            if model.settings.strictMode {
                strictSection(editable: editable)
            }

            Section("Protection") {
                Toggle("Protection on", isOn: $model.settings.protectionEnabled)
                Button {
                    showPicker = true
                } label: {
                    HStack {
                        Text("Apps to watch").foregroundColor(.white)
                        Spacer()
                        Text("\(model.selection.itemCount) selected").foregroundColor(.goMuted)
                    }
                }
            }
            .disabled(!editable)

            Section("About you") {
                TextField("Your name", text: $model.settings.userName)
                TextField("What are you working on?", text: $model.settings.goal)
            }
            .disabled(!editable)

            Section {
                Stepper("Every \(model.settings.nudgeIntervalMinutes) min of use", value: $model.settings.nudgeIntervalMinutes, in: 1...30)
                Stepper("\"Continue\" needs 2 taps, \(model.settings.continueWaitSeconds) s apart", value: $model.settings.continueWaitSeconds, in: 3...60)
                Toggle("Mindful pause on every open", isOn: $model.settings.mindfulOpen)
                Toggle("Notification with each reminder", isOn: $model.settings.notifyOnNudge)
            } header: {
                Text("Go do your work")
            } footer: {
                Text("The shield appears over Instagram & co. every few minutes of use. iOS doesn't allow a countdown on the shield, so \"Continue\" must be tapped twice.")
            }
            .disabled(!editable)

            Section {
                Stepper(
                    model.settings.dailyLimitMinutes == 0 ? "No daily limit" : "\(model.settings.dailyLimitMinutes) min a day",
                    value: $model.settings.dailyLimitMinutes,
                    in: 0...240,
                    step: 5
                )
            } header: {
                Text("Daily limit")
            } footer: {
                Text("All watched apps together. After the limit they stay locked until midnight (3 emergency unlocks of 5 min a day).")
            }
            .disabled(!editable)

            Section("Focus hours") {
                Toggle("Lock apps during focus hours", isOn: $model.settings.focusEnabled)
                if model.settings.focusEnabled {
                    DatePicker("From", selection: minuteBinding(\.focusStartMinute), displayedComponents: .hourAndMinute)
                    DatePicker("To", selection: minuteBinding(\.focusEndMinute), displayedComponents: .hourAndMinute)
                    WeekdayPicker(selected: $model.settings.focusWeekdays)
                        .padding(.vertical, 4)
                }
            }
            .disabled(!editable)

            Section {
                Toggle("Strict mode", isOn: $model.settings.strictMode)
            } header: {
                Text("Strict mode")
            } footer: {
                Text("Changing settings needs a 60-second wait, and sprints can't be stopped early.")
            }
            .disabled(!editable)

            Section {
                Text("GoWork · made to get you back to work.")
                    .foregroundColor(.goMuted)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.goInk.ignoresSafeArea())
        .familyActivityPicker(isPresented: $showPicker, selection: $model.selection)
        .onReceive(timer) { date in
            now = date
            if let at = unlockAt, date >= at {
                unlockAt = nil
                model.strictUnlockedUntil = date.addingTimeInterval(180)
            }
        }
    }

    @ViewBuilder
    private func strictSection(editable: Bool) -> some View {
        Section("Strict mode is on") {
            if editable {
                Label("Unlocked for \(countdown(to: model.strictUnlockedUntil))", systemImage: "lock.open.fill")
                    .foregroundColor(.goMint)
            } else if let at = unlockAt {
                Label("Unlocking in \(countdown(to: at))… still sure?", systemImage: "hourglass")
                    .foregroundColor(.goVolt)
            } else {
                Button {
                    unlockAt = Date().addingTimeInterval(60)
                } label: {
                    Label("Wait 60 s to edit for 3 minutes", systemImage: "lock.fill")
                }
            }
        }
    }

    private func countdown(to date: Date) -> String {
        let seconds = max(0, Int(date.timeIntervalSince(now)))
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func minuteBinding(_ keyPath: WritableKeyPath<GoSettings, Int>) -> Binding<Date> {
        Binding(
            get: {
                let minute = model.settings[keyPath: keyPath]
                return Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
            },
            set: { date in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                model.settings[keyPath: keyPath] = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            }
        )
    }
}
