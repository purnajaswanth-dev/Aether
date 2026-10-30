import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium select-none border transition-colors",
  {
    variants: {
      variant: {
        default: "bg-subtle text-text-secondary border-border",
        success: "bg-success-subtle text-success border-success/20",
        warning: "bg-warning-subtle text-warning border-warning/20",
        danger: "bg-danger-subtle text-danger border-danger/20",
        info: "bg-info-subtle text-info border-info/20",
        accent: "bg-accent-subtle text-accent border-accent/20",
        neutral: "bg-muted text-text-tertiary border-border",
      },
      size: {
        sm: "text-[11px] px-1.5 py-0.2",
        default: "text-xs px-2 py-0.5",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

const dotVariants = cva("w-1.5 h-1.5 rounded-full shrink-0", {
  variants: {
    variant: {
      default: "bg-text-secondary",
      success: "bg-success",
      warning: "bg-warning",
      danger: "bg-danger",
      info: "bg-info",
      accent: "bg-accent",
      neutral: "bg-text-tertiary",
    },
  },
  defaultVariants: {
    variant: "default",
  },
});

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {
  showDot?: boolean;
}

function Badge({ className, variant, size, showDot = true, children, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant, size }), className)} {...props}>
      {showDot && <span className={cn(dotVariants({ variant }))} />}
      <span>{children}</span>
    </div>
  );
}

export { Badge, badgeVariants };

