# Quorum-Based-Mutual-Exclusion
1. Implementation of a tree-based quorum system
2. Seven server nodes in the system
    *  The servers are logically arranged as a binary tree
    *  Initially, all servers are in an unlocked state
    *  Unlocked server receives a client’s REQUEST, the server gets locked by that client and sends a GRANT message to the client.
    *  Locked server receives a client’s REQUEST, the server adds that client’s REQUEST to its queue ordered
       by the timestamp of the REQUEST.
    *  On receiving RELEASE message from client the server is locked by that client, the server checks is queue. If there is a REQUEST in its queue,
       the server dequeues the REQUEST at the head of the queue, sends a GRANT to the corresponding
       client and stays in the locked state. Otherwise (queue is empty), the server becomes unlocked
3. Five client nodes in the system
    *  Client independently generates its requests to enter the critical section
    *  Waits for a period of time that is uniformly distributed  time units before trying to enter the critical section.
    *  Sends a REQUEST to a quorum of servers and waits for GRANTS
    *  REQUEST has been granted by all nodes in the selected quorum, the client can enter its critical section
4. Quorum definition
    *  Root of the tree + a quorum of left subtree
    *  Root of the tree + a quorum of right subtree
    *  A quorum of left subtree + a quorum of right subtree
    *  If the tree is a singleton node, then the tree’s quorum consists of that node
5. Data Collection
    *  Report on latency by changing time between the request and time spent in critical section
    *  Number of messages exchanged between client and server to complete 20 simulated run
    