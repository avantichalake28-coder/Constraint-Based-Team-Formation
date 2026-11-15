import { useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { getApiErrorMessage } from "../../../utils/apiError";
import { adminService } from "../adminService";

export function AdminAnalyticsPage() {
  const metadata = useQuery({
    queryKey: ["admin", "metadata"],
    queryFn: adminService.getAllMetadata,
  });

  return (
    <>
      <header className="student-page-header">
        <p className="student-page-header__eyebrow">REPORTING</p>
        <h1>Analytics</h1>
        <p>Only values returned by the backend are shown; no statistics are inferred from incomplete data.</p>
      </header>
      <section className="student-card">
        <div className="student-card__heading">
          <div><p className="student-card__eyebrow">AVAILABLE DATA</p><h2>Application metadata</h2></div>
        </div>
        {metadata.isPending ? (
          <div aria-live="polite" className="student-state"><LoaderCircle aria-hidden="true" className="spin" size={22} />Loading metadata…</div>
        ) : metadata.isError ? (
          <div className="student-state student-state--error" role="alert"><strong>Could not load metadata.</strong><p>{getApiErrorMessage(metadata.error)}</p></div>
        ) : metadata.data.length ? (
          <dl className="student-preference-overview">
            {metadata.data.map((item) => (
              <div className="student-preference-overview__item" key={item.metaKey}>
                <strong>{item.metaKey}</strong><p>{item.metaValue}</p>
              </div>
            ))}
          </dl>
        ) : <p className="student-empty">No metadata entries are available.</p>}
        <p className="student-note">The backend has no Admin analytics APIs for student counts, department/CGPA distribution, mentor workload, or team statistics.</p>
      </section>
    </>
  );
}
