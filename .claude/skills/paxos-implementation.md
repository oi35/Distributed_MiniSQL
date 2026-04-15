---
name: paxos-implementation
description: Implement and debug Paxos consensus protocol for replica consistency
type: skill
---

# Paxos Implementation Skill

Use this skill when implementing or debugging Paxos consensus protocol.

## When to Use

- Implementing write operations with Paxos
- Debugging consensus failures
- Handling replica failures during consensus
- Optimizing Paxos performance

## Paxos Overview

### Roles

- **Proposer**: Primary replica that initiates proposals
- **Acceptor**: All replicas that vote on proposals
- **Learner**: Replicas that learn the chosen value

### Phases

1. **Prepare**: Proposer asks acceptors to promise
2. **Accept**: Proposer asks acceptors to accept value
3. **Commit**: Notify learners of chosen value

## Implementation

### Phase 1: Prepare

```java
public class PaxosProposer {
    
    public boolean prepare(long proposalNumber) {
        PrepareRequest request = PrepareRequest.newBuilder()
            .setProposalNumber(proposalNumber)
            .build();
        
        int promiseCount = 0;
        long highestAcceptedProposal = -1;
        byte[] highestAcceptedValue = null;
        
        // Send to all acceptors
        for (Replica replica : replicas) {
            PrepareResponse response = replica.prepare(request);
            if (response.getPromise()) {
                promiseCount++;
                if (response.getAcceptedProposal() > highestAcceptedProposal) {
                    highestAcceptedProposal = response.getAcceptedProposal();
                    highestAcceptedValue = response.getAcceptedValue().toByteArray();
                }
            }
        }
        
        // Need majority
        return promiseCount > replicas.size() / 2;
    }
}
```

### Phase 2: Accept

```java
public boolean accept(long proposalNumber, byte[] value) {
    AcceptRequest request = AcceptRequest.newBuilder()
        .setProposalNumber(proposalNumber)
        .setValue(ByteString.copyFrom(value))
        .build();
    
    int acceptCount = 0;
    
    // Send to all acceptors
    for (Replica replica : replicas) {
        AcceptResponse response = replica.accept(request);
        if (response.getAccepted()) {
            acceptCount++;
        }
    }
    
    // Need majority
    return acceptCount > replicas.size() / 2;
}
```

### Acceptor Logic

```java
public class PaxosAcceptor {
    private long promisedProposal = -1;
    private long acceptedProposal = -1;
    private byte[] acceptedValue = null;
    
    public PrepareResponse handlePrepare(PrepareRequest request) {
        long proposalNumber = request.getProposalNumber();
        
        if (proposalNumber > promisedProposal) {
            promisedProposal = proposalNumber;
            
            return PrepareResponse.newBuilder()
                .setPromise(true)
                .setAcceptedProposal(acceptedProposal)
                .setAcceptedValue(ByteString.copyFrom(acceptedValue))
                .build();
        }
        
        return PrepareResponse.newBuilder()
            .setPromise(false)
            .build();
    }
    
    public AcceptResponse handleAccept(AcceptRequest request) {
        long proposalNumber = request.getProposalNumber();
        
        if (proposalNumber >= promisedProposal) {
            promisedProposal = proposalNumber;
            acceptedProposal = proposalNumber;
            acceptedValue = request.getValue().toByteArray();
            
            return AcceptResponse.newBuilder()
                .setAccepted(true)
                .build();
        }
        
        return AcceptResponse.newBuilder()
            .setAccepted(false)
            .build();
    }
}
```

## Simplified Two-Phase Commit (教学版本)

For educational purposes, use simplified 2PC:

```java
public class SimplifiedConsensus {
    
    public boolean writeWithConsensus(byte[] key, byte[] value) {
        // Phase 1: Prepare all replicas
        for (Replica replica : replicas) {
            if (!replica.prepare(key, value)) {
                // Abort if any replica fails
                rollbackAll();
                return false;
            }
        }
        
        // Phase 2: Commit all replicas
        for (Replica replica : replicas) {
            replica.commit(key, value);
        }
        
        return true;
    }
    
    private void rollbackAll() {
        for (Replica replica : replicas) {
            replica.rollback();
        }
    }
}
```

## Handling Failures

### Proposer Failure

- New proposer uses higher proposal number
- Continues from last known state

### Acceptor Failure

- Continue if majority still available
- Failed acceptor catches up when recovered

### Network Partition

- Majority partition continues
- Minority partition blocks writes

## Optimization

### Optimization 1: Multi-Paxos

Elect a stable leader to skip Prepare phase:

```java
public class MultiPaxos {
    private Replica leader;
    private long leaderEpoch;
    
    public boolean write(byte[] key, byte[] value) {
        if (isLeader()) {
            // Skip prepare, directly accept
            return accept(leaderEpoch, value);
        } else {
            // Full Paxos
            return fullPaxos(key, value);
        }
    }
}
```

### Optimization 2: Batching

Batch multiple writes in one proposal:

```java
public boolean batchWrite(List<Write> writes) {
    byte[] batchValue = serializeWrites(writes);
    return paxos(batchValue);
}
```

## Testing

### Test: Basic Consensus

```java
@Test
void testBasicConsensus() {
    Paxos paxos = new Paxos(3); // 3 replicas
    boolean success = paxos.propose(1, "value".getBytes());
    assertTrue(success);
    
    // Verify all replicas have the value
    for (Replica replica : paxos.getReplicas()) {
        assertEquals("value", new String(replica.getValue()));
    }
}
```

### Test: Concurrent Proposals

```java
@Test
void testConcurrentProposals() {
    Paxos paxos = new Paxos(3);
    
    // Two proposers with different values
    CompletableFuture<Boolean> f1 = CompletableFuture.supplyAsync(
        () -> paxos.propose(1, "value1".getBytes())
    );
    CompletableFuture<Boolean> f2 = CompletableFuture.supplyAsync(
        () -> paxos.propose(2, "value2".getBytes())
    );
    
    // One should succeed
    assertTrue(f1.get() || f2.get());
    
    // All replicas should have same value
    byte[] value = paxos.getReplicas().get(0).getValue();
    for (Replica replica : paxos.getReplicas()) {
        assertArrayEquals(value, replica.getValue());
    }
}
```

### Test: Replica Failure

```java
@Test
void testReplicaFailure() {
    Paxos paxos = new Paxos(3);
    
    // Kill one replica
    paxos.getReplicas().get(2).shutdown();
    
    // Should still succeed with 2/3 majority
    boolean success = paxos.propose(1, "value".getBytes());
    assertTrue(success);
}
```

## Common Issues

### Issue: Livelock

**Symptom:** Proposals keep failing due to competing proposers

**Fix:** Use exponential backoff and randomization

```java
private void backoff(int attempt) {
    long delay = (long) (Math.pow(2, attempt) * 100 + Math.random() * 100);
    Thread.sleep(delay);
}
```

### Issue: Split Brain

**Symptom:** Two leaders in different partitions

**Fix:** Use epoch numbers and reject stale leaders

```java
if (request.getEpoch() < currentEpoch) {
    return Response.newBuilder()
        .setSuccess(false)
        .setMessage("Stale epoch")
        .build();
}
```

## Git Workflow

**IMPORTANT:** All code changes must follow this workflow:

1. **Work on develop branch:** All development work happens on the `develop` branch
2. **Commit frequently:** Commit after completing each logical unit of work
3. **Stage completion:** Only merge to `master` after:
   - All tests pass
   - Code review is complete
   - Stage/milestone is verified and meets requirements
4. **Merge to master:** Use Pull Request or direct merge after verification

**Commands:**
```bash
# Ensure you're on develop
git checkout develop

# Make changes and commit
git add <files>
git commit -m "type(scope): description"

# Push to remote develop
git push origin develop

# After stage completion and verification
git checkout master
git merge develop
git push origin master
```
