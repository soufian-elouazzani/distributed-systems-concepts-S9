# Client-server critical section

Design for a critical section shared by several clients, with one server, a FIFO waiting list, and a provided fault detector.

`REQUEST`, `GRANT`, `RELEASE`, and `FAILURE` travel on the message queues already built. This document does not describe that transport. It describes who stores the critical-section state, and where the fault detector is attached.

## Architecture

```text
                         +----------------------+
                         |        Server        |
                         |                      |
   Client A  <---------> |  holder : Client     |
   Client B  <---------> |  FIFO   : [ ... ]    |
   Client C  <---------> |                      |
                         +----------------------+
```

Both variants use the same four elements:

| Element | Role |
| --- | --- |
| Server | Authority that grants the critical section, one client at a time |
| Clients | Ask to enter, do their work, then release |
| FIFO queue | Clients whose `REQUEST` was accepted and who are waiting for `GRANT` |
| Holder | The client currently inside the critical section, or nobody |

Shared rules:

- There is at most one holder.
- `GRANT` order follows the order of accepted `REQUEST`s.
- A client appears at most once in { holder } ∪ queue.
- The client inside the critical section is the only one allowed to release it.

## Messages

| Message | Sender | Meaning |
| --- | --- | --- |
| `REQUEST(client)` | Client | Ask to enter |
| `GRANT(client)` | Server, or token holder | Permission to enter |
| `RELEASE(client)` | Holder | Leave the critical section |
| `FAILURE(client)` | Fault detector | This client is treated as crashed |

On the client, the normal sequence is always the same: send `REQUEST`, wait for `GRANT`, run the critical section, send `RELEASE`.

## Stateful server

The server keeps both pieces of information between messages:

```text
holder : Client | empty
queue  : FIFO queue of clients
```

The server is the only component that updates this state.

`REQUEST(c)`:

1. If `c` is already the holder or is already in the queue, the server ignores the duplicate.
2. If the holder is empty, `c` becomes the holder and receives `GRANT(c)`.
3. Otherwise, `c` is appended to the tail of the queue.

`RELEASE(c)`:

1. The server accepts the release only when `c` is the holder.
2. If the queue is empty, the holder becomes empty.
3. Otherwise, the server removes the head of the queue, makes it the new holder, and sends it `GRANT`.

```text
Client A                Server                  Client B
   | REQUEST(A) ----------> |                      |
   | <---------- GRANT(A)   |                      |
   | [critical section]     |                      |
   |                        | <----- REQUEST(B)    |
   |                        | queue = [B]          |
   | RELEASE(A) ----------> |                      |
   |                        | holder = B           |
   |                        | GRANT(B) ----------> |
```

The server remembers clients across requests. A server restart clears the holder and the queue, so clients send their `REQUEST` again.

## Stateless server

The server stores neither the holder nor the queue. Both travel inside a token:

```text
Token {
  holder : Client | empty
  queue  : FIFO queue of clients
}
```

There is a single token. It is held by the client inside the critical section, or by the server when nobody is inside and the queue is empty. In that idle case the server keeps only the idle token. It keeps no client session.

`REQUEST(c)` is delivered to the current token holder (the server when the token is idle, otherwise the holding client; the server only routes):

1. If the token is free, `c` becomes the holder and the token is sent to `c`. Receiving the token is the `GRANT`.
2. Otherwise, `c` is appended to the queue stored inside the token.

`RELEASE(c)` is performed by the client that holds the token:

1. If the queue is not empty, that client removes the head, makes it the holder, and forwards the token.
2. If the queue is empty, it returns the token to the server with an empty holder.

The server does not rebuild the queue. Every message that changes the critical section carries the token, so it carries the full state.

## Fault detector

The detector is a provided component. This design only fixes where it runs and when it is used. It is treated as a `FAILURE(client)` signal: from that signal on, the client is crashed and will not receive a `GRANT`.

The detector sits **on the server in both variants**. The server sees every client that participates, and a single observer keeps one opinion about each crash. Clients do not watch other clients for the critical section.

A client watches the server only to decide whether it must send its `REQUEST` again after a server crash. That watch does not choose who enters the critical section.

### When the server starts and stops watching

| Moment | Action |
| --- | --- |
| `REQUEST` accepted | The server starts watching that client |
| The client becomes the holder | Watching continues. This is the interval whose crash blocks everyone else |
| `RELEASE` accepted, and the client is no longer queued | The server stops watching it |
| Removed from the queue without entering | The server stops watching it |

A client is watched exactly while it is the holder or present in the queue.

### When `FAILURE` is applied

**The holder crashes.** Its release will not arrive. The server treats the crash as a `RELEASE`: the critical section is freed, and the head of the queue receives `GRANT` when a successor exists.

**A waiting client crashes.** It is removed from the FIFO queue. Clients behind it move up one place. It does not receive `GRANT`.

**A client that has already left.** No action. It is no longer watched.

```text
Client A (holder)        Server + detector         Queue [B, C]
        |                        |                        |
        X crash                  |                        |
                         FAILURE(A)                      |
                         same effect as RELEASE(A)       |
                         holder = B                      |
                         GRANT(B) ---------------------> B
                         queue = [C]
```

### How the two variants react

**Stateful server.** The queue and the holder are already in server memory. The detector and the state are in the same place. On `FAILURE`, the server updates that memory and sends at most one `GRANT`.

**Stateless server.** The queue is inside the token.

- If a **waiting** client crashes, the server sends `FAILURE(client)` to the token holder. The holder removes that client from the token's queue.
- If the **holder** crashes, the token disappears with it. The server, informed by its detector, creates one new empty token and tells the surviving clients. Each client that still has a `REQUEST` in progress sends it again with the same sequence number as the original request. The new holder rebuilds the FIFO queue by sorting those sequence numbers. The new token is created only after `FAILURE` of the holder.

Surviving clients keep their original request number, so FIFO order is preserved across the rebuild.

### Assumption on the detector

Freeing the critical section on a mere suspicion would let a second client enter while the first is still inside. This design assumes the provided detector reports `FAILURE` only for a real crash. Until that signal arrives, the holder remains the only client allowed to enter.

## Properties

**Exclusion.** At most one client runs the critical section. This follows from the single token, or from the server's single `holder` variable, and from sending a `GRANT` only after `RELEASE` or `FAILURE` of the previous holder.

**FIFO order.** A client already in the queue receives `GRANT` before a client whose `REQUEST` is accepted later.

**Progress after a crash.** A crash of the holder frees the critical section. A crash of a waiting client does not block the clients behind it.

## Summary

| | State on the server | State in the token |
| --- | --- | --- |
| Holder and FIFO queue | Server memory | Token held by the current holder |
| Role of the server | Decides and remembers | Routes messages, and keeps the token only while it is idle |
| Fault detector | On the server | On the server |
| Watching starts | `REQUEST` accepted | `REQUEST` accepted |
| Watching stops | Client has left both the queue and the critical section | Client has left both the queue and the critical section |
| Holder crash | The server applies an implicit `RELEASE` and grants the rest of its queue | The server creates a new token, and clients resend `REQUEST` with the same sequence number |
| Waiting-client crash | Removed from the server queue | Removed from the token queue by the holder, after the server's signal |
