/**
 * <h3>Engine E4: Group Validation &amp; Recommendation Engine</h3>
 *
 * <ul>
 *   <li><b>Course Alignment:</b> Computer Networks, DBMS, Software Engineering</li>
 *   <li><b>Requirements Mapped:</b> Cluster C4 (FR-12, FR-13, FR-14, FR-15)</li>
 *   <li><b>Primary Technical Problem Owned:</b> Verify team completeness, ensure zero duplicate/orphan students, and generate explainable satisfaction and audit reports for mentors.</li>
 *   <li><b>Primary Responsibility:</b> Validate membership invariants against all constraints, produce explainability reports on why preferences were or were not met, and support mentor approval/publication workflow.</li>
 *   <li><b>State / Data Owned:</b> Validation audit results, explanation metrics, mentor approval status, and publication history.</li>
 *   <li><b>Inputs:</b> Draft team assignments from E3; active constraints from E2; preferences and roster from E1.</li>
 *   <li><b>Outputs:</b> Validation integrity report, team quality metrics, explanation breakdown, and publication approval status.</li>
 * </ul>
 */
package com.teamformation.backend.engine.validation;

