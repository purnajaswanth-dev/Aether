import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center whitespace-nowrap rounded-control text-sm font-medium transition-colors duration-hover ease-standard focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 select-none",
  {
    variants: {
      variant: {
        default: "bg-accent text-white hover:bg-accent-hover active:bg-accent-hover shadow-lift",
        secondary: "bg-subtle text-text-primary border border-border hover:bg-muted active:bg-border",
        outline: "border border-border bg-transparent text-text-primary hover:bg-subtle active:bg-muted",
        ghost: "text-text-secondary hover:text-text-primary hover:bg-subtle active:bg-muted",
        danger: "bg-danger text-white hover:opacity-90 active:opacity-100 shadow-lift",
        dangerOutline: "border border-danger text-danger hover:bg-danger-subtle",
        subtle: "bg-accent-subtle text-accent hover:bg-accent/15",
      },
      size: {
        sm: "h-7 px-2.5 text-xs rounded-control",
        default: "h-8 px-3 text-sm rounded-control",
        lg: "h-9 px-4 text-base rounded-control",
        icon: "h-8 w-8 p-0 rounded-control",
        iconSm: "h-7 w-7 p-0 rounded-control",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
