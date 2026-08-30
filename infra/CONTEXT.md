# Production Delivery

The production-delivery context describes how CommonEx releases become authoritative in production and remain recoverable.

## Language

**Release**:
An immutable, validated production definition identified by a Git SHA and containing the allowlisted Compose and environment files.

**Activation**:
The serialized transaction that makes a release authoritative in configuration, running containers, and activation state.
_Avoid_: Deployment, when referring specifically to the activation transaction

**Activation Intent**:
The durable marker proving that an activation began but has not yet reached a durably confirmed terminal state.

**Retained Release**:
A release present in activation history and therefore eligible for manual rollback.

**Active Release**:
The release at the front of activation history whose validated files exactly match the active configuration.

**Deployment**:
The broader production-delivery process that prepares, activates, and verifies a release.
_Avoid_: Activation, when referring to the broader process
