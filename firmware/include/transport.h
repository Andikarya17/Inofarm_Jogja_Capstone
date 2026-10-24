#pragma once
#include <string>
#include <vector>
#include <cctype>
#include "core.h"

namespace inofarm {
inline bool privateIpv4(const std::string& host) {
    unsigned octets[4]={0,0,0,0}; unsigned index=0,digits=0; bool leadingZero=false;
    for(char c:host) {
        if(c=='.') { if(!digits || index>=3) return false; ++index;digits=0;leadingZero=false; }
        else if(c>='0' && c<='9') { if(digits && leadingZero) return false;if(!digits) leadingZero=c=='0';if(++digits>3) return false; octets[index]=octets[index]*10+unsigned(c-'0');if(octets[index]>255) return false; }
        else return false;
    }
    if(index!=3 || !digits) return false;
    return octets[0]==10 || octets[0]==127 || (octets[0]==192 && octets[1]==168) || (octets[0]==172 && octets[1]>=16 && octets[1]<=31);
}
inline bool transportAllowed(const std::string& url, bool allowHttpBench) {
    const bool tls=url.compare(0,8,"https://")==0;
    const bool plain=url.compare(0,7,"http://")==0;
    if(!tls && (!plain || !allowHttpBench)) return false;
    const size_t start=tls?8:7,slash=url.find('/',start);
    if(slash==std::string::npos || slash==start || url.back()!='/') return false;
    const std::string authority=url.substr(start,slash-start);
    if(authority.find_first_of("@?#%\\ \r\n\t")!=std::string::npos) return false;
    const size_t colon=authority.find(':');
    std::string host=authority.substr(0,colon);
    if(host.empty() || host.front()=='.' || host.back()=='.') return false;
    for(char c:host) if(!std::isalnum(static_cast<unsigned char>(c)) && c!='-' && c!='.') return false;
    if(colon!=std::string::npos) {
        const std::string port=authority.substr(colon+1);unsigned value=0;
        if(port.empty() || port.size()>5) return false;
        for(char c:port) { if(c<'0'||c>'9') return false;value=value*10+unsigned(c-'0'); }
        if(value==0 || value>65535) return false;
    }
    if(url.substr(slash).find_first_of("?#\\ \r\n\t")!=std::string::npos) return false;
    return tls || privateIpv4(host);
}
enum class Delivery { Retry, PersistAck, PreserveBlocked };
inline Delivery deliveryDecision(int code,bool parsed,bool persisted,const std::string& id,const std::string& returned) {
    if((code==200 || code==201) && parsed && acknowledgementMatches(id,persisted,returned)) return Delivery::PersistAck;
    if(code==409 || code==422 || code==413 || code==400) return Delivery::PreserveBlocked;
    return Delivery::Retry;
}
// Fair scan across a backlog, including after transient failures; does not delete anything.
inline bool precedesCandidate(const std::string& candidate,const std::string& selected,const std::string& after) {
    if(selected.empty()) return true;
    const bool next=candidate>after,current=selected>after;
    return next!=current ? next : candidate<selected;
}
class AlarmDeadline {
    uint32_t until=0; bool armed=false;
public:
    void start(uint32_t now,uint32_t duration) { until=now+duration;armed=true; }
    void stop() { armed=false; }
    bool expired(uint32_t now) { if(armed && static_cast<int32_t>(now-until)>=0) { armed=false;return true; } return false; }
};
}
