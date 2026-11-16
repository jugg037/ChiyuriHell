# Chiyuri model orchestration and avatar execution

## One central identity

The central chat model remains the only user-facing Chiyuri voice. Specialist
adapters are invoked only for explicit tasks and their results are returned to
the central model for synthesis. Specialist outputs are treated as untrusted
data. The central model, personality prompt and conversation memory remain
separate from provider implementations.

`ModelCoordinator` is the orchestration seam. It accepts a bounded list of
specialist tasks, routes each task by capability, records success or failure,
and sends the combined evidence to the central chat capability. A specialist
cannot replace the central chat role. The current cloud adapter still supports
chat only; vision, image, video and speech adapters must be implemented and
registered separately before those capabilities become available.

## Avatar is the only desktop executor

`AvatarActionGateway` is the only planned route from AI decisions to external
desktop interactions. It checks that the live avatar advertises the requested
capability and evaluates the permission scope/risk before forwarding the
semantic action to the avatar runtime.

There is deliberately no direct mouse, keyboard, shell or OS fallback in this
gateway. A future Unity runtime must receive the action, visibly perform the
corresponding avatar animation/gesture, synchronize the real interaction, and
return an action result. If the avatar is disconnected or does not advertise a
capability, the action must fail closed rather than being executed invisibly.

Sensitive, destructive and external actions require explicit approval. User
shutdown, disconnect and uninstall remain authoritative.

## Current implementation boundary

This change introduces tested Python contracts and orchestration logic. It does
not yet connect a live Unity avatar, implement a desktop input adapter, inspect
a supplied Unity project, or provide working avatar movement. Those require
the Unity-side bridge/runtime and the actual avatar project assets.
