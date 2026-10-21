import SwiftUI

struct GoCard<Content: View>: View {
    var border: Color = .goStroke
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.goPanel)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(border, lineWidth: 1)
        )
    }
}

struct SectionLabel: View {
    let text: String
    var color: Color = .goMuted

    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.bold))
            .tracking(1.2)
            .foregroundColor(color)
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var color: Color = .goVolt

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.heavy))
            .foregroundColor(.goInk)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .background(color.opacity(configuration.isPressed ? 0.8 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Color.goPanelHigh.opacity(configuration.isPressed ? 0.7 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct StatBlock: View {
    let value: String
    let label: String
    var color: Color = .white

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.title2.weight(.heavy))
                .foregroundColor(color)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption)
                .foregroundColor(.goMuted)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "GO DO YOUR WORK." with the last word in yellow.
struct Headline: View {
    let text: String
    var accent: Color = .goVolt
    var size: CGFloat = 44

    var body: some View {
        let words = text.split(separator: " ").map(String.init)
        let head = words.dropLast().joined(separator: " ")
        let tail = words.last ?? ""
        (Text(head.isEmpty ? "" : head + " ").foregroundColor(.white) + Text(tail).foregroundColor(accent))
            .font(.system(size: size, weight: .black))
            .lineSpacing(-4)
    }
}

/// S M T W T F S toggles for focus hours (1 = Sunday).
struct WeekdayPicker: View {
    @Binding var selected: Set<Int>

    var body: some View {
        let symbols = Calendar.current.veryShortWeekdaySymbols
        HStack(spacing: 6) {
            ForEach(1...7, id: \.self) { day in
                let on = selected.contains(day)
                Button {
                    if on { selected.remove(day) } else { selected.insert(day) }
                } label: {
                    Text(symbols[day - 1])
                        .font(.subheadline.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .foregroundColor(on ? .goInk : .white)
                        .background(on ? Color.goVolt : Color.goPanelHigh)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}
