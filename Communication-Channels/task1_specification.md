# Task 1: Functional Specification for Communication Channels

This document outlines the operational specs, synchronization rules, and edge cases for full-duplex byte-stream communications.


## Overview & Core Principles

* **Byte-Oriented Flux:** Communication takes place over a continuous flux of raw bytes rather than discrete packets. This design leverages TCP socket behavior to guarantee strict FIFO ordering and zero data loss across streams.
* **Full-Duplex Data Flow:** Data moves independently in both directions simultaneously across established channels.
* **Task Synchronization:** The `accept()` and `connect()` methods serve as explicit blocking synchronization points between interacting tasks.


## Class: `Broker`

The `Broker` handles setup, port management, and channel creation between tasks using underlying network sockets.

### `Broker(String name)`
* Creates a named message broker instance bound to an identifier.
* **Edge Case:** If a broker with the specified name already exists in the system registry, initialization fails and raises an exception to prevent duplicate routing endpoints.

### `Channel accept(int port)`
* Opens a server-side socket listener on the given port to wait for connection attempts from other tasks.
* **Concurrency & Multiple Tasks Constraint:** If $N$ tasks simultaneously attempt to call `accept()` on the exact same port, only **one** task is granted ownership of that port. Subsequent tasks will be blocked or rejected depending on whether the port remains occupied.
* **Synchronization & Blocking:** Calling `accept()` completely halts the execution of the calling task until a matching `connect()` is executed by a client task on the same target port. Once paired, it returns a fully initialized full-duplex `Channel`.

### `Channel connect(String name, int port)`
* Requests a connection to a specific `Broker` identified by `name` on a given `port`.
* **Synchronization & Blocking:** Suspends the caller task until the destination broker's `accept()` call acknowledges and establishes the connection.
* **Edge Cases:** If the target broker name is invalid, `connect()` immediately throws an error. If the broker exists but no task is actively calling `accept()` on that port, the caller task remains blocked until a server task opens the port.


## Class: `Channel`

The `Channel` wraps the input and output socket streams to facilitate full-duplex transmission and controlled graceful teardowns.

### `int read(byte[] bytes, int offset, int length)`
* Pulls a flux of bytes from the incoming stream into the target array starting at the specified offset.
* **Blocking Behavior:** Blocks the reading task if the socket's receive buffer contains no data. The thread unblocks as soon as 1 or more bytes become available.
* **Return Cases:** Returns the exact number of bytes transferred. Returns `-1` if the remote node has closed its transmission stream and all remaining bytes in the channel have been read.

### `int write(byte[] bytes, int offset, int length)`
* Pushes a flux of bytes from the source array into the outgoing stream.
* **Blocking Behavior:** Blocks if the TCP socket send buffer reaches capacity, waiting until the receiving end consumes bytes and frees space.
* **Return Cases:** Returns the total number of bytes written, or `-1` if the channel has been disconnected.

### `void disconnect()`
* Manages stream closure while avoiding standard TCP socket teardown issues where remaining buffered data can be lost.
* **Half-Close (Demi-Disconnect) Logic:**
  1. The node calling `disconnect()` shuts down only its outgoing stream (write side), signaling to the remote node that no further data will be sent.
  2. The connected remote node keeps its reading channel active to process all remaining in-flight bytes sitting in the channel buffers.
  3. Once the channel buffer is completely drained, the remote node receives an end-of-stream signal (`-1`), informing it that the peer has disconnected.
  4. The remote node can then safely finalize its local teardown, ensuring zero data loss during channel closure.

### `boolean disconnected()`
* Checks the current operational status of the channel endpoints.
* Returns `true` if the local write stream has been shut down or if both sides have completed the full disconnection process.


## Class: `Task`

### `Task(Broker b, Runnable r)`
* Links an execution thread to a designated `Broker` instance so that all socket and channel operations within the task resolve to the correct broker context.

### `static Broker getBroker()`
* Returns the `Broker` bound to the currently running task thread. Returns `null` if called outside of a active `Task` context.