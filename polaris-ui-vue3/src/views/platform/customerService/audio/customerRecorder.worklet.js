class CustomerRecorderProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0]?.[0]
    if (channel?.length) {
      const samples = channel.slice()
      this.port.postMessage(samples, [samples.buffer])
    }
    return true
  }
}
registerProcessor('customer-recorder', CustomerRecorderProcessor)
