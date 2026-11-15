import { Sparkles } from "lucide-react";

interface BrandMarkProps {
  compact?: boolean;
}

export function BrandMark({ compact = false }: BrandMarkProps) {
  return (
    <div className={`brand-mark${compact ? " brand-mark--compact" : ""}`}>
      <Sparkles aria-hidden="true" className="brand-mark__icon" strokeWidth={2.5} />
      <span>SkillMatch</span>
    </div>
  );
}
