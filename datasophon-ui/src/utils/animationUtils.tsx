export const invokeExcuteAnimation = (fn) => {
  let id
  const invokeStopAnimation = () => {
    if (id) {
      cancelAnimationFrame(id)
      id = invokeStopAnimation.id = undefined
    }
  }

  id = invokeStopAnimation.id = requestAnimationFrame(async () => {
    await fn(invokeStopAnimation)

    invokeStopAnimation()
  })


  return invokeStopAnimation
}


