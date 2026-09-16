# Task 1: Method Specifications

This document defines the formal specification (contract, preconditions, postconditions, blocking behaviors, and exceptions) for the `Broker`, `Channel`, and `Task` abstract classes.


## 1. Abstract Class: `Broker`
-stream by flux of bytes to insure FIFO/lossless we gonna use TCP
-Read/Write , synchro accept and connect for the tasks 
-we need to synchronize the tasks
-
the TCP that insures lossless and FIFO not working in the diconnection because it needs a stable connection and stables steps for a disconnect
disconnect: 
for a demi disconnect like we keep the connected node reading since still informations in the channel and when there's nothing more in the channel we can tell the node taht is connected that the other node is not connected and this way we close the communication channel

### `Broker(String name)`
* **Description:** Constructs a named message broker instance.
* **Pre-conditions:** `name` must be a non-null, non-empty string that uniquely identifies this broker within the system registry.
* **Post-conditions:** A new `Broker` instance is created and bound to the specified name.
* **Exceptions:** Throws `IllegalArgumentException` if `name` is `null` or if a broker with the given name is already registered.


### `Channel accept(int port)`
* **Description:** Listens for and accepts an incoming connection request on the specified port.
* **Pre-conditions:** 
  * `port` must be a valid port number ($0 \le 	ext{port} \le 65535$).
  * The port must not already have an active listener on this broker.
* **Post-conditions:** A new connected `Channel` instance is created and established between the local listening task and the remote connecting task.
* **Blocking Behavior:** **Blocking.** The calling thread is suspended until a matching `connect(name, port)` request is initiated by another task.
* **Return Value:** A connected `Channel` object ready for full-duplex communication.
* **Exceptions:** Throws `IllegalStateException` or returns `null` if the connection attempt is interrupted or fails.


### `Channel connect(String name, int port)`
* **Description:** Initiates a connection to a target broker identified by `name` on a specific `port`.
* **Pre-conditions:** 
  * `name` must correspond to an active, registered target broker.
  * `port` must match an active listener registered on the target broker.
* **Post-conditions:** Establishes a paired, full-duplex `Channel` between the calling task and the accepting task on the remote broker.
* **Blocking Behavior:** **Blocking.** Suspends the calling thread until the target broker's `accept(port)` receives and confirms the connection request.
* **Return Value:** A connected `Channel` instance linked to the remote task's channel.
* **Exceptions:** Throws `IllegalArgumentException` if the target broker `name` is invalid or not found.


## 2. Abstract Class: `Channel`

### `int read(byte[] bytes, int offset, int length)`
* **Description:** Reads up to `length` bytes of data from the channel's input buffer into the target byte array starting at `offset`.
* **Pre-conditions:**
  * `bytes` must not be `null`.
  * $0 \le 	ext{offset} < 	ext{bytes.length}$.
  * $0 \le 	ext{length} \le 	ext{bytes.length} - 	ext{offset}$.
  * The channel must not be in a disconnected state prior to the call.
* **Post-conditions:** Up to `length` bytes are written into `bytes` starting at index `offset`. The internal buffer pointers are updated accordingly.
* **Blocking Behavior:** **Blocking.** If no data is available in the channel's buffer, the calling thread blocks until at least 1 byte becomes available or the channel is disconnected.
* **Return Value:** 
  * The actual number of bytes read ($> 0$).
  * Returns `-1` if the channel has been disconnected by the remote peer or local thread (end-of-stream).
* **Exceptions:** Throws `IndexOutOfBoundsException` if `offset` or `length` parameters are invalid.



### `int write(byte[] bytes, int offset, int length)`
* **Description:** Writes up to `length` bytes of data from the source array starting at `offset` into the channel's output buffer.
* **Pre-conditions:**
  * `bytes` must not be `null`.
  * $0 \le 	ext{offset} < 	ext{bytes.length}$.
  * $0 \le 	ext{length} \le 	ext{bytes.length} - 	ext{offset}$.
  * The channel must not be disconnected.
* **Post-conditions:** Data bytes are copied into the channel's internal circular buffer, advancing the write pointers.
* **Blocking Behavior:** **Blocking.** If the internal circular buffer is full, the calling thread blocks until sufficient space becomes available or the channel is closed.
* **Return Value:** 
  * The number of bytes successfully written ($> 0$).
  * Returns `-1` if the channel is disconnected while attempting to write.
* **Exceptions:** Throws `IndexOutOfBoundsException` if array bounds are violated.


### `void disconnect()`
* **Description:** Closes the channel for both reading and writing and signals disconnection to the remote peer.
* **Pre-conditions:** None (safe to invoke multiple times or on an already disconnected channel).
* **Post-conditions:** Releases associated internal circular buffers. Any blocked `read()` or `write()` threads on either end of the channel are unblocked and immediately return `-1`.
* **Blocking Behavior:** **Non-blocking.** Returns immediately after updating channel state and notifying waiting threads.


### `boolean disconnected()`
* **Description:** Queries the current connection status of the channel.
* **Pre-conditions:** None.
* **Post-conditions:** Does not alter the state of the channel.
* **Blocking Behavior:** **Non-blocking.**
* **Return Value:** `true` if either the local or remote endpoint has called `disconnect()`; `false` otherwise.



## 3. Abstract Class: `Task`

### `Task(Broker b, Runnable r)`
* **Description:** Initializes a specialized execution thread bound to a specific `Broker` context.
* **Pre-conditions:** Both `b` (`Broker`) and `r` (`Runnable`) must not be `null`.
* **Post-conditions:** A `Task` instance is instantiated with its thread-local broker reference assigned to `b`.


### `static Broker getBroker()`
* **Description:** Retrieves the `Broker` instance bound to the currently executing `Task` thread.
* **Pre-conditions:** The calling thread must be an instance of `Task` (or executing within a task context).
* **Blocking Behavior:** **Non-blocking.**
* **Return Value:** The `Broker` associated with the current running task, or `null` if called outside of a `Task` thread context.