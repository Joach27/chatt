import React from "react";

export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }
  static getDerivedStateFromError(error) {
    return { hasError: true };
  }
  componentDidCatch(error, errorInfo) {
    // log error if needed
  }
  render() {
    if (this.state.hasError) {
      return <div style={{color:'red'}}>Une erreur est survenue lors du rendu du message.</div>;
    }
    return this.props.children;
  }
}