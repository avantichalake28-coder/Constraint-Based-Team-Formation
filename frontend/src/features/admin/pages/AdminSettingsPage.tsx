import { useQuery } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { getApiErrorMessage } from "../../../utils/apiError";
import { adminService } from "../adminService";

export function AdminSettingsPage() {
  const metadata = useQuery({
    queryKey: ["admin", "metadata"],
    queryFn: adminService.getAllMetadata,
  });

  return (
    <>
      <header className="student-page-header">
        <p className="student-page-header__eyebrow">CONFIGURATION</p>
        <h1>System settings</h1>
        <p>Application metadata is read-only through the current backend API.</p>
      </header>
      <section className="student-card">
        <div className="student-card__heading">
          <div><p className="student-card__eyebrow">READ ONLY</p><h2>System metadata</h2></div>
          <button className="student-button student-button--secondary" disabled={metadata.isFetching} onClick={() => void metadata.refetch()} type="button">Refresh</button>
        </div>
        {metadata.isPending ? (
          <div aria-live="polite" className="student-state"><LoaderCircle aria-hidden="true" className="spin" size={22} />Loading settings…</div>
        ) : metadata.isError ? (
          <div className="student-state student-state--error" role="alert"><strong>Could not load settings.</strong><p>{getApiErrorMessage(metadata.error)}</p></div>
        ) : metadata.data.length ? (
          <dl className="student-preference-overview">
            {metadata.data.map((item) => (
              <div className="student-preference-overview__item" key={item.metaKey}>
                <strong>{item.metaKey}</strong><p>{item.metaValue}</p>
              </div>
            ))}
          </dl>
        ) : <p className="student-empty">No settings metadata is available.</p>}
        <p className="student-note">No metadata create/update or administrative configuration API is exposed, so values cannot be edited here.</p>
      </section>
    </>
  );
}
