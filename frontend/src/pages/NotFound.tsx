import { Link } from 'react-router';
import { Empty } from '../components/ui';

export default function NotFound() {
  return (
    <Empty>
      <h2>Page not found</h2>
      <Link to="/">Back to rooms</Link>
    </Empty>
  );
}
