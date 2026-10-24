#pragma once
#include <cstdint>
#include <string>

namespace inofarm {
enum class Screen { Monitoring, Phase, Ppm, Review, Pending, Error };
enum class Button { Ok, Back, Right, Left };
enum class Phase { Vegetatif, Generatif };

struct Draft {
    Phase phase = Phase::Vegetatif; // Unconfirmed editor selection, never active owner phase.
    std::string digits;
    bool measured = false;
};

class Menu {
public:
    Screen screen = Screen::Monitoring;
    Draft draft;
    unsigned cursor = 0;
    void press(Button key) {
        switch (screen) {
        case Screen::Monitoring:
            if (key == Button::Ok) { draft = Draft{}; screen = Screen::Phase; }
            break;
        case Screen::Phase:
            if (key == Button::Back) screen = Screen::Monitoring;
            else if (key == Button::Right || key == Button::Left) draft.phase = draft.phase == Phase::Vegetatif ? Phase::Generatif : Phase::Vegetatif;
            else if (key == Button::Ok) { draft.digits = "000000"; cursor = 0; screen = Screen::Ppm; }
            break;
        case Screen::Ppm:
            if (key == Button::Back) screen = Screen::Phase;
            else if (key == Button::Right || key == Button::Left) {
                int digit = draft.digits[cursor] - '0';
                digit = (digit + (key == Button::Right ? 1 : 9)) % 10;
                draft.digits[cursor] = char('0' + digit);
            } else if (key == Button::Ok) {
                if (++cursor == draft.digits.size()) { draft.measured = true; screen = Screen::Review; }
            }
            break;
        case Screen::Review:
            if (key == Button::Back) { cursor = 0; screen = Screen::Ppm; }
            // OK persistence is handled by coordinator; cannot claim saved here.
            break;
        case Screen::Pending: if (key == Button::Back) screen = Screen::Monitoring; break;
        case Screen::Error: if (key == Button::Back) screen = Screen::Review; break;
        }
    }
    void stored(bool success) { screen = success ? Screen::Pending : Screen::Error; }
    uint32_t ppm() const { return draft.measured ? static_cast<uint32_t>(std::stoul(draft.digits)) : 0; }
};

inline bool acknowledgementMatches(const std::string& queuedId, bool persisted, const std::string& returnedId) {
    return persisted && !queuedId.empty() && queuedId == returnedId;
}
inline bool contains(int weekday, int minute, int startDay, int startMinute, int endMinute) {
    int point = weekday * 1440 + minute;
    int start = startDay * 1440 + startMinute;
    int end = startDay * 1440 + endMinute;
    if (endMinute <= startMinute) end += 1440;
    return (point >= start && point < end) || (point + 10080 >= start && point + 10080 < end);
}
} // namespace inofarm
