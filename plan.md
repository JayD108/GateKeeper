# Build Plan

## 1. Architecture Diagram
```mermaid
graph TD
    subgraph P1 [P1 Phone - Commuter]
        G[OS Geofence API] -->|Exits L1 Office| T{Time > 5PM?}
        T -->|Yes| A[App Wakes Up]
        G -->|Enters 500m L2| ALARM[Trigger Proximity Alarm]
        A -->|Internet OK| F1[Write to Firestore]
        ALARM -->|Internet OK| F1
        ALARM -->|No Internet| SMS[Send 'GATEKEEPER:ALARM' SMS]
        A -->|Starts| FS[Foreground Service: Live GPS]
        FS -->|Every 10s| F1
        Widget[Honk Gate Widget] --> ALARM
    end

    subgraph Cloud [Firebase]
        F1 --> DB[(Firestore)]
        DB --> CF[Cloud Function]
        CF -->|Generate Push| FCM[Firebase Cloud Messaging]
    end

    subgraph P2 [P2 Phone - Home]
        FCM -->|High Priority| P2App[P2 App wakes up]
        SMS -.->|Intercepts| P2App
        P2App --> FSAlarm[Full Screen Intent Loop]
        FSAlarm --> ACK[User Taps 'Got It']
        ACK --> F2[Write 'Ack:True' to Firestore]
        F2 -.-> P1
    end