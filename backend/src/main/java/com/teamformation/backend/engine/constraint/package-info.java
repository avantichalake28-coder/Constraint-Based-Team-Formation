/**
 * <h3>Engine E2: Constraint Management Engine</h3>
 *
 * <ul>
 *   <li><b>Course Alignment:</b> DSA, DBMS</li>
 *   <li><b>Requirements Mapped:</b> Cluster C2 (FR-04, FR-05, FR-06, FR-11)</li>
 *   <li><b>Primary Technical Problem Owned:</b> Represent and evaluate mentor-defined hard and soft constraints, detect contradictory rules, and identify infeasible configurations.</li>
 *   <li><b>Primary Responsibility:</b> Store constraints, validate rule consistency, check roster divisibility, and produce feasibility reports before formation starts.</li>
 *   <li><b>State / Data Owned:</b> Constraint definitions (team size, skill prerequisites, exclusions, soft weights) and project rule settings.</li>
 *   <li><b>Inputs:</b> Fixed team size, hard/soft constraints, eligible participant count from E1.</li>
 *   <li><b>Outputs:</b> Validated constraint specification or detailed infeasibility diagnostics.</li>
 * </ul>
 */
package com.teamformation.backend.engine.constraint;

