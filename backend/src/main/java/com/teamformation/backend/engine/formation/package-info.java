/**
 * <h3>Engine E3: Compatibility &amp; Group Formation Engine</h3>
 *
 * <ul>
 *   <li><b>Course Alignment:</b> DSA, Operating Systems</li>
 *   <li><b>Requirements Mapped:</b> Cluster C3 (FR-08, FR-09, FR-10)</li>
 *   <li><b>Primary Technical Problem Owned:</b> Form complete, fixed-size teams while resolving overlapping, mutual, or conflicting teammate preferences deterministically without AI.</li>
 *   <li><b>Primary Responsibility:</b> Execute constraint satisfaction/search routines, calculate preference scores, apply deterministic tie-breaking, and generate draft team assignments.</li>
 *   <li><b>State / Data Owned:</b> Candidate group combinations, compatibility scores, and deterministic tie-breaking results.</li>
 *   <li><b>Inputs:</b> Eligible student list and preferences from E1; validated hard and soft constraints from E2.</li>
 *   <li><b>Outputs:</b> Complete draft team assignments and preference fulfillment metrics.</li>
 * </ul>
 */
package com.teamformation.backend.engine.formation;

